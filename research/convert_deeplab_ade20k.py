# Reproducible conversion: TF model zoo DeepLabV3 MobileNetV2 ADE20K frozen graph -> LiteRT (TFLite).
# Source: http://download.tensorflow.org/models/deeplabv3_mnv2_ade20k_train_2018_12_03.tar.gz (tensorflow/models, Apache-2.0)
# Run with venv-conv (Python 3.11, tensorflow-cpu 2.18.1). Input: float32 NHWC in [-1,1] (x/127.5-1).
# Output: logits [1,(S-1)/4+1,(S-1)/4+1,151]; channel 0 = "other", channel c = ADE20K class c (floor=4, road=7, sidewalk=12, stairs=54, stairway=60, step=122 in this 1-based layout).
import sys, tensorflow as tf
from tensorflow.python.framework import tensor_util
tf1=tf.compat.v1; tf1.disable_eager_execution()
PB='models/seg/deeplabv3_mnv2_ade20k_train_2018_12_03/frozen_inference_graph.pb'
S=int(sys.argv[1]) if len(sys.argv)>1 else 257
gd=tf1.GraphDef(); gd.ParseFromString(open(PB,'rb').read())
f8=(S-1)//8+1; f4=(S-1)//4+1
for n in gd.node:
    if '_output_shapes' in n.attr: del n.attr['_output_shapes']
    if n.op=='AvgPool': n.attr['ksize'].list.i[:]=[1,f8,f8,1]                      # image-pooling kernel was fixed for 513
    if n.name=='ResizeBilinear/size': n.attr['value'].tensor.CopyFrom(tensor_util.make_tensor_proto([f8,f8],dtype=tf.int32))
    if n.name in ('decoder/ResizeBilinear/size','decoder/ResizeBilinear_1/size'): n.attr['value'].tensor.CopyFrom(tensor_util.make_tensor_proto([f4,f4],dtype=tf.int32))
g=tf.Graph()
with g.as_default():
    ph=tf1.placeholder(tf.float32,[1,S,S,3],name='input')
    out,=tf1.import_graph_def(gd,input_map={'sub_7:0':ph},return_elements=['logits/semantic/BiasAdd:0'],name='')
    with tf1.Session(graph=g) as s:
        for q in (False,True):
            c=tf1.lite.TFLiteConverter.from_session(s,[ph],[out])
            if q: c.optimizations=[tf.lite.Optimize.DEFAULT]
            fn=f'models/seg/deeplabv3_mnv2_ade20k_{S}_{"dynint8" if q else "f32"}.tflite'; open(fn,'wb').write(c.convert()); print(fn)
