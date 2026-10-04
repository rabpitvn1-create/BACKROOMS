"""Render a rolling atlas from the approved rounded cube, with all six faces."""
from pathlib import Path
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]/'android-apk/app/src/main/assets'
N=128; FRAMES=24
CAM=np.array([3.,2.6,7.5]);CAM=CAM/np.linalg.norm(CAM)*8
forward=-CAM/np.linalg.norm(CAM)
right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right)
up=np.cross(right,forward)
v,u=np.mgrid[0:N,0:N]
# A slightly wider camera contains the cube's rotating diagonal without clipping.
orig=CAM+(u[...,None]-(N-1)/2)/N*3.5*right-((v[...,None]-(N-1)/2)/N*3.5)*up
layouts={1:[(0,0)],2:[(-.43,.43),(.43,-.43)],3:[(-.43,.43),(0,0),(.43,-.43)],4:[(-.43,.43),(.43,.43),(-.43,-.43),(.43,-.43)],5:[(-.43,.43),(.43,.43),(0,0),(-.43,-.43),(.43,-.43)],6:[(x,y) for x in [-.43,.43] for y in [-.43,0,.43]]}
centers=np.array([(x,y,1.065) for x,y in layouts[1]]+[(x,y,-1.065) for x,y in layouts[6]]+[(x,1.065,-y) for x,y in layouts[2]]+[(x,-1.065,y) for x,y in layouts[5]]+[(1.065,y,-x) for x,y in layouts[3]]+[(-1.065,y,x) for x,y in layouts[4]])
def shape(p,material=False):
 q=np.abs(p)-.82
 box=np.linalg.norm(np.maximum(q,0),axis=-1)+np.minimum(np.max(q,axis=-1),0)-.18
 hole=np.full(box.shape,100.)
 for c in centers:hole=np.minimum(hole,np.linalg.norm(p-c,axis=-1)-.155)
 dist=np.maximum(box,-hole)
 return (dist,-hole>box+.0001) if material else dist
light=np.array([-3.,5.,7.]);light/=np.linalg.norm(light)
atlas=Image.new('RGBA',(N*FRAMES,N))
for frame in range(FRAMES):
 a=frame/FRAMES*2*np.pi;b=a*2
 rx=np.array([[1,0,0],[0,np.cos(a),-np.sin(a)],[0,np.sin(a),np.cos(a)]])
 ry=np.array([[np.cos(b),0,np.sin(b)],[0,1,0],[-np.sin(b),0,np.cos(b)]])
 rotation=ry@rx
 def sdf(p,material=False):return shape(p@rotation,material)
 t=np.zeros((N,N));active=np.ones((N,N),bool)
 for _ in range(75):
  p=orig+t[...,None]*forward;d=sdf(p)
  active&=(d>.00035)&(t<12)
  if not active.any():break
  t+=np.where(active,np.maximum(d,.0001),0)
 p=orig+t[...,None]*forward;hit=t<12;e=.001
 normal=np.stack([sdf(p+np.eye(3)[k]*e)-sdf(p-np.eye(3)[k]*e) for k in range(3)],axis=-1)
 normal/=np.maximum(np.linalg.norm(normal,axis=-1,keepdims=True),1e-8)
 _,black=sdf(p,True)
 shade=.34+.66*np.maximum(normal@light,0)
 ao=np.ones((N,N))
 for distance in [.025,.06,.12]:ao-=np.maximum(distance-sdf(p+normal*distance),0)*1.4
 half=light-forward;half/=np.linalg.norm(half)
 spec=np.maximum(normal@half,0)**65*.17
 color=np.where(black[...,None],np.array([.042,.046,.037]),np.array([.95,.91,.80]))*shade[...,None]*np.clip(ao,.45,1)[...,None]+spec[...,None]
 color=np.clip(color,0,1)**(1/1.5)
 rgba=np.zeros((N,N,4),np.uint8);rgba[...,:3]=(color*255).astype(np.uint8);rgba[...,3]=hit*255
 atlas.paste(Image.fromarray(rgba),(N*frame,0))
 print(f'frame {frame+1}/{FRAMES}',flush=True)
atlas.save(ROOT/'dice'/'roll-3d.png')
