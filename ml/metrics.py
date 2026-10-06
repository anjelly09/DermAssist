"""Evaluation and calibration, separated from training to prevent test-set tuning."""
import numpy as np
from scipy.optimize import minimize_scalar
from scipy.special import softmax, logsumexp
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score


def wilson(successes, count):
    if count == 0: return None
    z = 1.96
    p = successes / count
    middle = (p + z*z/(2*count))/(1+z*z/count)
    width = z*np.sqrt(p*(1-p)/count+z*z/(4*count*count))/(1+z*z/count)
    return [float(middle-width), float(middle+width)]


def calibrate(logits, labels):
    def nll(log_temperature):
        scaled = logits / np.exp(log_temperature)
        return float(np.mean(logsumexp(scaled, axis=1)-scaled[np.arange(len(labels)), labels]))
    result = minimize_scalar(nll, bounds=(-2.3, 2.3), method="bounded")
    return float(np.exp(result.x))


def calibration_metrics(probs, labels):
    confidence = probs.max(1)
    correct = probs.argmax(1) == labels
    ece = 0.
    for low in np.arange(0., 1., .1):
        mask = (confidence >= low) & (confidence < low+.1 if low < .9 else confidence <= 1)
        if mask.any(): ece += mask.mean()*abs(correct[mask].mean()-confidence[mask].mean())
    target = np.eye(probs.shape[1])[labels]
    return dict(ece=float(ece), brier=float(np.mean(np.sum((probs-target)**2, axis=1))),
                nll=float(-np.log(np.clip(probs[np.arange(len(labels)), labels], 1e-8, 1)).mean()))


def classification_metrics(labels, probs, classes):
    pred = probs.argmax(1)
    correct = pred == labels
    report = classification_report(labels, pred, labels=list(range(len(classes))), target_names=classes, zero_division=0, output_dict=True)
    return dict(n=len(labels), accuracy=float(correct.mean()), accuracy_ci95=wilson(int(correct.sum()),len(labels)),
                macro_f1=float(f1_score(labels,pred,labels=list(range(len(classes))),average="macro",zero_division=0)),
                per_class=report, confusion_matrix=confusion_matrix(labels,pred,labels=list(range(len(classes)))).tolist(),
                calibration=calibration_metrics(probs,labels))


def normalized(values):
    return values / np.maximum(np.linalg.norm(values,axis=-1,keepdims=True),1e-8)


def distances(embedding, centroids):
    return 1 - np.max(normalized(embedding) @ normalized(centroids).T, axis=1)


def choose_rejection(probs, labels, distance, ood_probs, ood_distance):
    """Classroom criterion, not a clinical threshold. Only calibration data allowed.

    Maximize coverage subject to >=75% observed accepted accuracy, at least eight
    accepted calibration cases, and <=10% OOD false acceptance. If infeasible,
    return reject-all. These small-sample criteria do not establish safety.
    """
    best = dict(enabled=False, confidence_threshold=1.01, distance_threshold=0., coverage=0.)
    for threshold in np.linspace(.4,.95,23):
        for radius in np.quantile(distance, [.2,.35,.5,.65,.8,.9,.95,1.]):
            accepted = (probs.max(1)>=threshold)&(distance<=radius)
            ood_accepted = (ood_probs.max(1)>=threshold)&(ood_distance<=radius)
            if accepted.sum()<8: continue
            acc = float((probs.argmax(1)[accepted]==labels[accepted]).mean())
            if acc>=.75 and ood_accepted.mean()<=.1 and accepted.mean()>best['coverage']:
                best=dict(enabled=True,confidence_threshold=float(threshold),distance_threshold=float(radius),
                          coverage=float(accepted.mean()),accepted_accuracy=acc,accepted_n=int(accepted.sum()),
                          ood_false_acceptance=float(ood_accepted.mean()),ood_n=len(ood_probs))
    return best


def accepted_mask(probs, distance, policy):
    return (probs.max(1)>=policy['confidence_threshold']) & (distance<=policy['distance_threshold']) & policy['enabled']


def selective_metrics(labels, probs, distance, policy):
    accepted=accepted_mask(probs,distance,policy)
    correct=probs.argmax(1)==labels
    n=int(accepted.sum())
    return dict(accepted_n=n,total_n=len(labels),coverage=float(accepted.mean()),
                rejected_fraction=float(1-accepted.mean()),
                accepted_accuracy=float(correct[accepted].mean()) if n else None,
                accepted_accuracy_ci95=wilson(int(correct[accepted].sum()),n))
