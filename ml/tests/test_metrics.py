import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import numpy as np
from scipy.special import softmax
from metrics import calibrate, distances, choose_rejection, accepted_mask, wilson


def test_temperature_fitting_reduces_overconfident_error_nll():
    logits=np.array([[10.,0.],[10.,0.],[0.,10.],[0.,10.]])
    y=np.array([0,1,1,0])
    t=calibrate(logits,y)
    nll=lambda probs: -np.log(probs[np.arange(4),y]).mean()
    assert t>1
    assert nll(softmax(logits/t,axis=1))<nll(softmax(logits,axis=1))


def test_no_feasible_rejection_policy_rejects_everything():
    p=np.tile([.9,.1],(20,1)); y=np.ones(20,dtype=int); distance=np.zeros(20)
    policy=choose_rejection(p,y,distance,p,distance)
    assert not policy['enabled']
    assert not accepted_mask(p,distance,policy).any()


def test_cosine_distance_and_empty_interval():
    assert np.allclose(distances(np.array([[2.,0.],[0.,2.]]),np.array([[7.,0.]])),[0.,1.])
    assert wilson(0,0) is None
    assert wilson(8,10)[0]<.8<wilson(8,10)[1]
