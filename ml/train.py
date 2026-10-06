"""Reproducible classical baselines -> CNN transfer -> calibration -> mobile export."""
import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import time

os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL','2')
os.environ.setdefault('TF_NUM_INTRAOP_THREADS','4')
os.environ.setdefault('TF_NUM_INTEROP_THREADS','2')
os.environ.setdefault('OMP_NUM_THREADS','4')
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter
import tensorflow as tf
from scipy.special import softmax
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.neighbors import KNeighborsClassifier
from sklearn.metrics import f1_score, roc_auc_score
from metrics import *
from prepare_data import CLASSES, SEED

SIZE=160


def image_array(path, augmentation_seed=None):
    image=Image.open(path).convert('RGB').resize((SIZE,SIZE),Image.Resampling.BILINEAR)
    if augmentation_seed is not None:
        rng=np.random.default_rng(augmentation_seed)
        if rng.random()<.5: image=image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        image=image.rotate(float(rng.uniform(-10,10)),resample=Image.Resampling.BILINEAR)
        image=ImageEnhance.Brightness(image).enhance(float(rng.uniform(.85,1.15)))
    return np.asarray(image,dtype=np.float32)/127.5-1


def handcrafted(images):
    rows=[]
    for image in images:
        rgb=(image+1)/2
        gray=rgb.mean(-1)
        hist=np.concatenate([np.histogram(rgb[:,:,c],bins=16,range=(0,1),density=True)[0] for c in range(3)])
        thumbnail=np.asarray(Image.fromarray(np.uint8(gray*255)).resize((8,8)),dtype=np.float32).ravel()/255
        texture=[gray.mean(),gray.std(),np.abs(np.diff(gray,axis=0)).mean(),np.abs(np.diff(gray,axis=1)).mean()]
        rows.append(np.concatenate([hist,thumbnail,texture]))
    return np.asarray(rows)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--data-dir',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--assets',type=Path,default=Path('app/src/main/assets'))
    parser.add_argument('--fine-tune-epochs',type=int,default=4)
    args=parser.parse_args()
    out=args.output; out.mkdir(parents=True,exist_ok=True)
    args.assets.mkdir(parents=True,exist_ok=True)
    tf.keras.utils.set_random_seed(SEED)
    tf.config.experimental.enable_op_determinism()
    rows=json.loads((args.data_dir/'manifest.json').read_text())
    indices={split:np.array([i for i,r in enumerate(rows) if r['split']==split]) for split in ['train','tune','calibration','test','ood_calibration','ood_test']}
    train,tune,cal,test=[indices[s] for s in ['train','tune','calibration','test']]
    labels=np.array([CLASSES.index(r['label']) if r['label'] in CLASSES else -1 for r in rows])
    print('Loading',len(rows),'prepared images',flush=True)
    images=np.stack([image_array(args.data_dir/r['file']) for r in rows])
    # The held-out indices exist, but no test predictions or metrics are examined
    # until model selection, temperature, and rejection policy are frozen below.
    features=handcrafted(images)
    baseline_models={}
    selection={}
    for name in ['logistic','knn']:
        best_score=-1
        for value in ([.1,1.,10.] if name=='logistic' else [3,7,15]):
            classifier=(LogisticRegression(C=value,max_iter=1500,class_weight='balanced',random_state=SEED)
                        if name=='logistic' else KNeighborsClassifier(n_neighbors=value,weights='distance'))
            model=make_pipeline(StandardScaler(),classifier)
            model.fit(features[train],labels[train])
            score=f1_score(labels[tune],model.predict(features[tune]),average='macro')
            if score>best_score: baseline_models[name]=model; best_score=score; selection[name]=dict(parameter=value,tune_macro_f1=float(score))
    print('Classical baseline selection:',selection,flush=True)
    print('Loading ImageNet MobileNetV2 (alpha=0.35)',flush=True)
    backbone=tf.keras.applications.MobileNetV2(input_shape=(SIZE,SIZE,3),alpha=.35,include_top=False,weights='imagenet',pooling='avg')
    backbone.trainable=False
    embeddings=backbone.predict(images,batch_size=32,verbose=0)
    augmented=np.concatenate([images[train]]+[np.stack([image_array(args.data_dir/rows[i]['file'],SEED+int(i)+v*10000) for i in train]) for v in [1,2]])
    augmented_labels=np.tile(labels[train],3)
    augmented_embeddings=backbone.predict(augmented,batch_size=32,verbose=0)
    head=tf.keras.Sequential([tf.keras.layers.Input(shape=(embeddings.shape[1],)),
                             tf.keras.layers.Dense(64,activation='relu',kernel_regularizer=tf.keras.regularizers.l2(1e-4)),
                             tf.keras.layers.Dropout(.3),tf.keras.layers.Dense(len(CLASSES))],name='screening_head')
    loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True)
    weights={i:len(train)/(len(CLASSES)*sum(labels[train]==i)) for i in range(len(CLASSES))}
    head.compile(optimizer=tf.keras.optimizers.Adam(1e-3),loss=loss,metrics=['accuracy'])
    print('Training frozen-feature head',flush=True)
    h1=head.fit(augmented_embeddings,augmented_labels,epochs=60,batch_size=32,verbose=2,
                class_weight=weights,validation_data=(embeddings[tune],labels[tune]),
                callbacks=[tf.keras.callbacks.EarlyStopping(monitor='val_loss',patience=8,restore_best_weights=True)])
    inputs=tf.keras.Input(shape=(SIZE,SIZE,3),name='image')
    embedding=backbone(inputs,training=False)
    logits=head(embedding)
    model=tf.keras.Model(inputs,logits)
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),loss=loss,metrics=['accuracy'])
    frozen_values={weight.name:weight.numpy().copy() for weight in model.weights}
    model.save_weights(out/'frozen.weights.h5')
    frozen_loss=float(model.evaluate(images[tune],labels[tune],verbose=0)[0])
    backbone.trainable=True
    for layer in backbone.layers[:-24]: layer.trainable=False
    for layer in backbone.layers:
        if isinstance(layer,tf.keras.layers.BatchNormalization): layer.trainable=False
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),loss=loss,metrics=['accuracy'])
    print('Fine-tuning final convolution blocks; batch-normalization frozen',flush=True)
    h2=model.fit(augmented,augmented_labels,epochs=args.fine_tune_epochs,batch_size=24,verbose=2,
                class_weight=weights,validation_data=(images[tune],labels[tune]),
                callbacks=[tf.keras.callbacks.EarlyStopping(monitor='val_loss',patience=2,restore_best_weights=True)])
    tuned_loss=float(model.evaluate(images[tune],labels[tune],verbose=0)[0])
    selected='fine_tuned' if tuned_loss<frozen_loss else 'frozen_transfer'
    if selected=='frozen_transfer':
        for weight in model.weights: weight.assign(frozen_values[weight.name])
    model.save(out/'classifier.keras')
    mobile=tf.keras.Model(inputs,tf.keras.layers.Concatenate(name='logits_and_embedding')([model(inputs,training=False),backbone(inputs,training=False)]))
    outputs=mobile.predict(images,batch_size=32,verbose=0)
    all_logits=outputs[:,:len(CLASSES)]; all_embedding=outputs[:,len(CLASSES):]
    centroids=np.stack([normalized(all_embedding[train][labels[train]==i]).mean(0) for i in range(len(CLASSES))])
    temperature=calibrate(all_logits[cal],labels[cal])
    all_probs=softmax(all_logits/temperature,axis=1)
    all_distance=distances(all_embedding,centroids)
    oc=indices['ood_calibration']; ot=indices['ood_test']
    policy=choose_rejection(all_probs[cal],labels[cal],all_distance[cal],all_probs[oc],all_distance[oc])
    print('Frozen selection and calibration:',selected,temperature,policy,flush=True)
    converter=tf.lite.TFLiteConverter.from_keras_model(mobile)
    converter.optimizations=[tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types=[tf.float16]
    tflite=converter.convert()
    model_path=args.assets/'screening.tflite'; model_path.write_bytes(tflite)
    config=dict(schema_version=1,model_id='scin-mobilenetv2-20261006',model_sha256=hashlib.sha256(tflite).hexdigest(),
                classes=CLASSES,input_size=SIZE,normalization='RGB / 127.5 - 1',embedding_size=int(all_embedding.shape[1]),
                temperature=temperature,centroids=centroids.tolist(),policy=policy,
                quality=dict(min_luminance=8.,max_luminance=247.,min_laplacian_variance=5.),
                intended_use='Educational prototype only. Not clinically validated.',
                training_manifest_sha256=hashlib.sha256((args.data_dir/'manifest.json').read_bytes()).hexdigest())
    (args.assets/'screening.json').write_text(json.dumps(config,indent=2))
    (out/'selection.json').write_text(json.dumps(dict(baselines=selection,selected=selected,frozen_tune_loss=frozen_loss,fine_tune_loss=tuned_loss,temperature=temperature,policy=policy),indent=2))
    # FINAL TEST: no parameter adjustment after this point.
    majority=np.zeros((len(test),len(CLASSES))); majority[:,collections.Counter(labels[train]).most_common(1)[0][0]]=1
    report=dict(classes=CLASSES,seed=SEED,split_counts={k:len(v) for k,v in indices.items()},
                selection=json.loads((out/'selection.json').read_text()),baselines={'majority':classification_metrics(labels[test],majority,CLASSES)},
                neural=classification_metrics(labels[test],all_probs[test],CLASSES),
                uncalibrated=calibration_metrics(softmax(all_logits[test],axis=1),labels[test]),
                selective=selective_metrics(labels[test],all_probs[test],all_distance[test],policy),
                ood=dict(n=len(ot),false_acceptance=float(accepted_mask(all_probs[ot],all_distance[ot],policy).mean()),
                         confidence_auroc=float(roc_auc_score(np.r_[np.ones(len(test)),np.zeros(len(ot))],np.r_[all_probs[test].max(1),all_probs[ot].max(1)]))),
                model_bytes=len(tflite),fairness={},robustness={})
    for name,baseline in baseline_models.items(): report['baselines'][name]=classification_metrics(labels[test],baseline.predict_proba(features[test]),CLASSES)
    for group in ['1–3','4–6','7–10','unknown']:
        def bucket(row):
            try:
                x=int(row['monk']); return '1–3' if x<=3 else '4–6' if x<=6 else '7–10'
            except (ValueError,TypeError): return 'unknown'
        subgroup=np.array([i for i in test if bucket(rows[i])==group],dtype=int)
        report['fairness'][group]=dict(n=len(subgroup),insufficient_evidence=len(subgroup)<20,
             metrics=classification_metrics(labels[subgroup],all_probs[subgroup],CLASSES) if len(subgroup) else None)
    interpreter=tf.lite.Interpreter(model_content=tflite,num_threads=2); interpreter.allocate_tensors()
    input_index=interpreter.get_input_details()[0]['index']; output_index=interpreter.get_output_details()[0]['index']
    quantized=[]; durations=[]
    for i in test:
        interpreter.set_tensor(input_index,images[i:i+1]); before=time.perf_counter();interpreter.invoke()
        durations.append((time.perf_counter()-before)*1000); quantized.append(interpreter.get_tensor(output_index)[0])
    quantized=np.asarray(quantized)
    qprobs=softmax(quantized[:,:len(CLASSES)]/temperature,axis=1)
    report['export']=dict(max_abs_logit_delta=float(np.max(np.abs(quantized[:,:len(CLASSES)]-all_logits[test]))),
                        top1_agreement=float(np.mean(qprobs.argmax(1)==all_probs[test].argmax(1))),
                        desktop_median_ms=float(np.median(durations)),desktop_p95_ms=float(np.percentile(durations,95)),
                        quantized=classification_metrics(labels[test],qprobs,CLASSES))
    for name in ['blur','dark','bright']:
        corrupted=[]
        for i in test:
            im=Image.fromarray(np.uint8(np.clip((images[i]+1)*127.5,0,255)))
            im=im.filter(ImageFilter.GaussianBlur(4)) if name=='blur' else ImageEnhance.Brightness(im).enhance(.3 if name=='dark' else 1.7)
            corrupted.append(np.asarray(im,dtype=np.float32)/127.5-1)
        result=mobile.predict(np.asarray(corrupted),batch_size=32,verbose=0)
        probs=softmax(result[:,:len(CLASSES)]/temperature,axis=1)
        report['robustness'][name]=dict(classification=classification_metrics(labels[test],probs,CLASSES),
                                      selective=selective_metrics(labels[test],probs,distances(result[:,len(CLASSES):],centroids),policy))
    rng=np.random.default_rng(SEED)
    bootstrap=[f1_score(labels[test][idx],all_probs[test].argmax(1)[idx],labels=list(range(len(CLASSES))),average='macro',zero_division=0) for idx in [rng.integers(0,len(test),len(test)) for _ in range(500)]]
    report['neural']['macro_f1_bootstrap_ci95']=np.percentile(bootstrap,[2.5,97.5]).tolist()
    # Deployment decision is distinct from threshold fitting. Never loosen a policy
    # or change learned weights based on the final test set. This educational run
    # stays research-only until independent validation and clinical review.
    config['deployment']=dict(suggestions_enabled=False,
        reason='The installed research model has not been validated to offer condition suggestions. It ran locally, but your concern remains unassessed.',
        test_accuracy=report['neural']['accuracy'],test_cases=len(test),
        accepted_test_accuracy=report['selective']['accepted_accuracy'],accepted_test_cases=report['selective']['accepted_n'],
        status='RESEARCH_ONLY_NOT_SCREENING_READY')
    (args.assets/'screening.json').write_text(json.dumps(config,indent=2))
    (out/'metrics.json').write_text(json.dumps(report,indent=2))
    (out/'training_history.json').write_text(json.dumps(dict(frozen=h1.history,fine_tuned=h2.history),indent=2))
    # Export exact normalized input + expected full output for Kotlin parity tests.
    golden_dir=Path('app/src/androidTest/assets'); golden_dir.mkdir(parents=True,exist_ok=True)
    golden=images[test[0]].astype('<f4'); (golden_dir/'golden_input.bin').write_bytes(golden.tobytes())
    interpreter.set_tensor(input_index,golden[None]);interpreter.invoke()
    (golden_dir/'golden_output.json').write_text(json.dumps(interpreter.get_tensor(output_index)[0].tolist()))
    (golden_dir/'golden_source.json').write_text(json.dumps(dict(case_id=rows[test[0]]['case_id'],source='SCIN',source_path=rows[test[0]]['image_path'],license='https://github.com/google-research-datasets/scin/blob/main/LICENSE',modifications='Resized to 160x160 RGB and normalized to float32 [-1,1]')))
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,ax=plt.subplots(figsize=(6,5));matrix=np.asarray(report['neural']['confusion_matrix'])
    ax.imshow(matrix,cmap='Blues');ax.set(xticks=range(3),yticks=range(3),xticklabels=CLASSES,yticklabels=CLASSES,xlabel='Predicted class',ylabel='Reference label',title='Held-out SCIN cases · no abstention')
    for i in range(3):
        for j in range(3): ax.text(j,i,str(matrix[i,j]),ha='center',va='center',color='white' if matrix[i,j]>matrix.max()/2 else 'black')
    fig.tight_layout();fig.savefig(out/'confusion_matrix.png',dpi=160);plt.close(fig)
    fig,axes=plt.subplots(1,2,figsize=(9,3))
    for ax,(stage,history) in zip(axes,[('Frozen backbone',h1.history),('Fine-tuned blocks',h2.history)]):
        ax.plot(history['loss'],label='Training');ax.plot(history['val_loss'],label='Tune');ax.set(title=stage,xlabel='Epoch',ylabel='Cross-entropy');ax.legend()
    fig.tight_layout();fig.savefig(out/'learning_curves.png',dpi=160);plt.close(fig)
    print(json.dumps(report,indent=2),flush=True)

if __name__=='__main__': main()
