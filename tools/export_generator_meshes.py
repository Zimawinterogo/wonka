"""Export the supplied Blockbench cubes + smooth tube meshes without voxelization.
Uses only Python's standard library. Run from any directory.
"""
from pathlib import Path
import json,math,struct,base64
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'src/main/resources/assets/whiteout'
FACES={
'north':lambda a,b:[(a[0],b[1],a[2]),(b[0],b[1],a[2]),(b[0],a[1],a[2]),(a[0],a[1],a[2])],
'south':lambda a,b:[(b[0],b[1],b[2]),(a[0],b[1],b[2]),(a[0],a[1],b[2]),(b[0],a[1],b[2])],
'east':lambda a,b:[(b[0],b[1],a[2]),(b[0],b[1],b[2]),(b[0],a[1],b[2]),(b[0],a[1],a[2])],
'west':lambda a,b:[(a[0],b[1],b[2]),(a[0],b[1],a[2]),(a[0],a[1],a[2]),(a[0],a[1],b[2])],
'up':lambda a,b:[(a[0],b[1],b[2]),(b[0],b[1],b[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2])],
'down':lambda a,b:[(a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],a[1],b[2]),(a[0],a[1],b[2])]}
NORMAL={'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
def sub(a,b):return [x-y for x,y in zip(a,b)]
def unit(a):
 size=math.sqrt(sum(x*x for x in a));assert size>1e-9
 return [x/size for x in a]
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
def face_normal(e,keys):return unit(cross(sub(e['vertices'][keys[1]],e['vertices'][keys[0]]),sub(e['vertices'][keys[2]],e['vertices'][keys[0]])))
def normals(e):
 result={key:[0.,0.,0.] for key in e['vertices']}
 for f in e['faces'].values():
  ks=f['vertices'];n=face_normal(e,ks)
  for i,key in enumerate(ks):
   a=unit(sub(e['vertices'][ks[(i-1)%len(ks)]],e['vertices'][key]));b=unit(sub(e['vertices'][ks[(i+1)%len(ks)]],e['vertices'][key]))
   weight=math.acos(max(-1,min(1,sum(x*y for x,y in zip(a,b)))))
   for k in range(3):result[key][k]+=n[k]*weight
 return {key:unit(v) for key,v in result.items()}
def low_faces(e):
 # v7 tubes use a regular ring grid. Keep endpoints/tangencies; sample curves.
 rings=max(int(k.split('_')[0][1:]) for k in e['vertices'])+1
 assert rings in (2,84),'Unsupported tube layout; update LOD exporter'
 ids=[0,1] if rings==2 else [c*21+j for c in range(4) for j in (0,4,8,12,16,20)]
 out=[]
 for ii,i in enumerate(ids if rings>2 else ids[:-1]):
  q=ids[(ii+1)%len(ids)]
  for j in range(0,32,4):
   k=(j+4)%32;ks=[f'v{i}_{j}',f'v{q}_{j}',f'v{q}_{k}',f'v{i}_{k}']
   out.append({'vertices':ks,'uv':{ks[0]:[1+j*30/32,1+i*30/rings],ks[1]:[1+j*30/32,1+(q if q else rings)*30/rings],ks[2]:[1+(j+4)*30/32,1+(q if q else rings)*30/rings],ks[3]:[1+(j+4)*30/32,1+i*30/rings]}})
 return out

def export(model,low=False):
 quads=[]
 for e in model['elements']:
  assert e.get('rotation',[0,0,0])==[0,0,0],'Do not silently discard model rotations'
  if not e.get('visibility',True):continue
  lamp=e['name']=='power indicator lamp'
  if e['type']=='cube':
   for side,f in e['faces'].items():
    points=FACES[side](e['from'],e['to']);u,v,U,V=f['uv'];uv=[(u,v),(U,v),(U,V),(u,V)]
    quads.append((lamp,[(*p,*t,*NORMAL[side]) for p,t in zip(points,uv)]))
  else:
   assert e['type']=='mesh';ns=normals(e)
   for f in (low_faces(e) if low else e['faces'].values()):
    keys=f['vertices'][:];assert len(keys)==4
    n=face_normal(e,keys)
    if sum(n[k]*ns[keys[0]][k] for k in range(3))<0:keys.reverse()
    quads.append((lamp,[(*e['vertices'][key],*f['uv'][key],*ns[key]) for key in keys]))
 data=bytearray(struct.pack('>II',0x57474D31,len(quads)))
 for lamp,verts in quads:
  data.extend(struct.pack('>?',lamp))
  for v in verts:data.extend(struct.pack('>8f',*(x/16 for x in v[:3]),v[3]/256,v[4]/256,*v[5:]))
 return bytes(data),len(quads)

if __name__=='__main__':
 (DEST/'meshes').mkdir(exist_ok=True)
 for name,game in [('new_generator','generator'),('old_generator','old_generator')]:
  model=json.loads((ROOT/'model-source/generators'/f'{name}.bbmodel').read_text(encoding='utf8'))
  for low in (False,True):
   data,count=export(model,low);path=DEST/'meshes'/f'{game}{"_far" if low else ""}.mesh';path.write_bytes(data);print(path.name,count,'quads')
  (DEST/'textures/block'/f'{name}_atlas.png').write_bytes(base64.b64decode(model['textures'][0]['source'].split(',',1)[1]))
