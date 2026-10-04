"""Rebuild Poker Dice textures (developer-only: numpy and Pillow)."""
from pathlib import Path
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]/'android-apk/app/src/main/assets'
N=256
CAM=np.array([3.0,2.6,7.5]); CAM=CAM/np.linalg.norm(CAM)*8
forward=-CAM/np.linalg.norm(CAM)
right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right)
up=np.cross(right,forward)
v,u=np.mgrid[0:N,0:N]
orig=CAM+(u[...,None]-(N-1)/2)/N*2.95*right-((v[...,None]-(N-1)/2)/N*2.95)*up
layouts={1:[(0,0)],2:[(-.43,.43),(.43,-.43)],3:[(-.43,.43),(0,0),(.43,-.43)],4:[(-.43,.43),(.43,.43),(-.43,-.43),(.43,-.43)],5:[(-.43,.43),(.43,.43),(0,0),(-.43,-.43),(.43,-.43)],6:[(x,y) for x in [-.43,.43] for y in [-.43,0,.43]]}
adjacent={1:(2,3),2:(3,1),3:(1,2),4:(1,5),5:(3,6),6:(2,4)}
def geometry(n):
 top,side=adjacent[n]
 centers=np.array([(x,y,1.065) for x,y in layouts[n]]+[(x,1.065,-y) for x,y in layouts[top]]+[(1.065,y,-x) for x,y in layouts[side]])
 def sdf(p,material=False):
  q=np.abs(p)-.82
  box=np.linalg.norm(np.maximum(q,0),axis=-1)+np.minimum(np.max(q,axis=-1),0)-.18
  hole=np.full(box.shape,100.)
  for c in centers:
   hole=np.minimum(hole,np.linalg.norm(p-c,axis=-1)-.155)
  dist=np.maximum(box,-hole)
  return (dist, -hole>box+.0001) if material else dist
 return sdf
light=np.array([-3.,5.,7.]);light/=np.linalg.norm(light)
for value in range(1,7):
 sdf=geometry(value);t=np.zeros((N,N));active=np.ones((N,N),bool)
 for _ in range(85):
  p=orig+t[...,None]*forward;d=sdf(p)
  active&=(d>.0002)&(t<12)
  t+=np.where(active,np.maximum(d,.0001),0)
 p=orig+t[...,None]*forward
 hit=t<12
 e=.001
 normal=np.stack([sdf(p+np.eye(3)[k]*e)-sdf(p-np.eye(3)[k]*e) for k in range(3)],axis=-1)
 normal/=np.maximum(np.linalg.norm(normal,axis=-1,keepdims=True),1e-8)
 _,black=sdf(p,True)
 diffuse=np.maximum(normal@light,0)
 shade=.34+.66*diffuse
 # Recess shadows follow the same 3D surfaces, not a drawn circle overlay.
 ao=np.ones((N,N))
 for distance in [.025,.06,.12]:
  ao-=np.maximum(distance-sdf(p+normal*distance),0)*1.4
 half=light-forward;half/=np.linalg.norm(half)
 spec=np.maximum(normal@half,0)**65*.17
 ivory=np.array([.95,.91,.80]);ink=np.array([.042,.046,.037])
 color=np.where(black[...,None],ink,ivory)*shade[...,None]*np.clip(ao,.45,1)[...,None]+spec[...,None]
 color=np.clip(color,0,1)**(1/1.5)
 rgba=np.zeros((N,N,4),np.uint8);rgba[...,:3]=(color*255).astype(np.uint8);rgba[...,3]=hit*255
 Image.fromarray(rgba).save(ROOT/'dice'/f'die-{value}.png')
