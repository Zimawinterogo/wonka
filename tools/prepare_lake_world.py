"""Create a verified COPY of the supplied directory world; never write to the input.
Only the preflighted emergency lamp block and scene JSON may change.
Python standard library only. Usage: python prepare_lake_world.py INPUT OUTPUT.zip REPORT.json
"""
import io, struct, zlib, json, zipfile, hashlib, sys
from pathlib import Path

class NBT:
 def __init__(self,data): self.f=io.BytesIO(data)
 def n(self,fmt): return struct.unpack('>'+fmt,self.f.read(struct.calcsize('>'+fmt)))[0]
 def string(self): return self.f.read(self.n('H')).decode('utf-8')
 def value(self,t):
  if 1<=t<=6: v=self.n({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  elif t==7: v=self.f.read(self.n('i'))
  elif t==8: v=self.string()
  elif t==9:
   sub,count=self.n('B'),self.n('i');v=(sub,[self.value(sub) for _ in range(count)])
  elif t==10:
   v={}
   while (sub:=self.n('B')): name=self.string();v[name]=self.value(sub)
  elif t in (11,12): v=[self.n('i' if t==11 else 'q') for _ in range(self.n('i'))]
  else: raise ValueError(t)
  return (t,v)
 def root(self): t=self.n('B');name=self.string();return name,self.value(t)

def string(s):
 b=s.encode('utf-8');return struct.pack('>H',len(b))+b
def encode(tag):
 t,v=tag
 if 1<=t<=6: return struct.pack('>'+{1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t],v)
 if t==7:return struct.pack('>i',len(v))+v
 if t==8:return string(v)
 if t==9:return bytes([v[0]])+struct.pack('>i',len(v[1]))+b''.join(encode(x) for x in v[1])
 if t==10:return b''.join(bytes([x[0]])+string(k)+encode(x) for k,x in v.items())+b'\0'
 if t in (11,12):return struct.pack('>i',len(v))+b''.join(struct.pack('>i' if t==11 else '>q',x) for x in v)
 raise ValueError(t)
def plain(tag):
 t,v=tag
 if t==10:return {k:plain(x) for k,x in v.items()}
 if t==9:return [plain(x) for x in v[1]]
 return v
def chunk(region,x,z):
 offset=((x//16)%32+((z//16)%32)*32)*4
 loc=int.from_bytes(region[offset:offset+3],'big')*4096
 assert loc,'missing chunk'
 length=int.from_bytes(region[loc:loc+4],'big');assert region[loc+4]==2,'expected zlib chunk'
 return offset,NBT(zlib.decompress(region[loc+5:loc+4+length])).root()
def section(root,y):return next(s[1] for s in root[1]['sections'][1][1] if s[1]['Y'][1]==y//16)
def states(root,x,y,z):
 bs=section(root,y)['block_states'][1];pal=bs['palette'][1][1];bits=max(4,(len(pal)-1).bit_length());per=64//bits
 indices=[0]*4096 if len(pal)==1 else [(bs['data'][1][i//per]>>((i%per)*bits))&((1<<bits)-1) for i in range(4096)]
 return bs,pal,indices,(y%16)*256+(z%16)*16+x%16
def get(root,pos):
 bs,pal,indices,i=states(root,*pos);return plain(pal[indices[i]])
def read_block(folder,pos):
 x,y,z=pos;r=(folder/f'region/r.{x//512}.{z//512}.mca').read_bytes();return get(chunk(r,x,z)[1][1],pos)
def patch_lamp(region):
 x,y,z=8373,76,8414;offset,(name,root)=chunk(region,x,z)
 assert get(root,(x,y,z))=={'Name':'minecraft:air'},'lamp position occupied'
 support=get(root,(x,y-1,z));assert support['Name']=='minecraft:spruce_slab' and support['Properties']['type']=='top'
 before=plain(root)
 bs,pal,indices,i=states(root,x,y,z)
 original_states=[plain(pal[index]) for index in indices]
 lamp=(10,{'Name':(8,'whiteout:emergency_lamp'),'Properties':(10,{'lit':(8,'false')})})
 pal.append(lamp);indices[i]=len(pal)-1
 bits=max(4,(len(pal)-1).bit_length());per=64//bits;packed=[0]*((4096+per-1)//per)
 for j,index in enumerate(indices):packed[j//per]|=index<<((j%per)*bits)
 bs['data']=(12,[v-(1<<64) if v>=1<<63 else v for v in packed])
 entities=root[1]['block_entities'][1][1]
 assert not any(tuple(e[1][k][1] for k in ('x','y','z'))==(x,y,z) for e in entities)
 entities.append((10,{'id':(8,'whiteout:emergency_lamp_entity'),'x':(3,x),'y':(3,y),'z':(3,z),
  'BackupCharge':(3,12000),'BackupChargeScale':(3,12000),'PowerLinked':(1,1),'GridPowered':(1,0),'SwitchEnabled':(1,1)}))
 raw=bytes([root[0]])+string(name)+encode(root)
 # Round-trip verifies all NBT types and data are preserved by the writer.
 assert NBT(raw).root()==(name,root)
 after=plain(root)
 for k in before:
  if k not in ('sections','block_entities'):assert before[k]==after[k],k
 assert before['block_entities']==after['block_entities'][:-1]
 for old,new in zip(before['sections'],after['sections']):
  if old['Y']!=y//16:assert old==new
  else:
   for k in old:
    if k!='block_states':assert old[k]==new[k],k
   _,final_palette,final_indices,_=states(root,x,y,z)
   for j in range(4096):
    if j!=i:assert original_states[j]==plain(final_palette[final_indices[j]]),j
 compressed=zlib.compress(raw);payload=struct.pack('>I',len(compressed)+1)+b'\2'+compressed
 sectors=(len(payload)+4095)//4096;assert sectors<256
 out=bytearray(region);out.extend(b'\0'*((-len(out))%4096));start=len(out)//4096
 out.extend(payload+b'\0'*(sectors*4096-len(payload)))
 out[offset:offset+4]=start.to_bytes(3,'big')+bytes([sectors])
 assert get(chunk(out,x,z)[1][1],(x,y,z))==plain(lamp)
 return bytes(out)

def main(source,destination,report):
 source=Path(source).resolve();destination=Path(destination).resolve();assert source not in destination.parents
 positions=[(8381,72,8366),(8411,72,8383),(8411,73,8383),(8404,71,8392),(8402,72,8392),(8355,72,8436),(8357,73,8436),(8375,76,8414),(8375,75,8414),(8373,76,8414),(8373,75,8414),(8385,72,8409),(8410,80,8383),(8410,81,8383)]
 blocks={str(pos):read_block(source,pos) for pos in positions}
 for pos in [(8410,80,8383),(8410,81,8383)]:assert blocks[str(pos)]['Name']=='minecraft:glass_pane'
 assert blocks[str((8385,72,8409))]['Name']=='minecraft:air'
 assert blocks[str((8375,76,8414))]['Name']=='whiteout:old_generator'
 scene=json.loads((source/'whiteout-lake-scene.json').read_text());assert scene['stage']==0 and not scene['decayApplied'],'reset source copy in game before preparing'
 scene.update(homeFueled=False,homeAttempted=False,homePulls=0,finalPhase=0,resetPending=False,waitingExit=[],journal={'changes':[]},conflict='',rearmAfterExit=False)
 changed={'whiteout-lake-scene.json':(json.dumps(scene,ensure_ascii=False,indent=2)+'\n').encode()}
 region='region/r.16.16.mca';changed[region]=patch_lamp((source/region).read_bytes())
 hashes={};preserved=0;datapacks=0
 destination.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as out:
  for path in sorted(source.rglob('*')):
   if not path.is_file():continue
   rel=path.relative_to(source).as_posix()
   if rel=='session.lock':continue
   original=path.read_bytes();data=changed.get(rel,original)
   out.writestr('world/'+rel,data)
   hashes[rel]={'before':hashlib.sha256(original).hexdigest(),'after':hashlib.sha256(data).hexdigest()}
   if rel not in changed:assert original==data;preserved+=1
   if rel.startswith('datapacks/Whiteout_Visit/'):datapacks+=1
 with zipfile.ZipFile(destination) as verify:
  assert verify.testzip() is None
  for rel,h in hashes.items():assert hashlib.sha256(verify.read('world/'+rel)).hexdigest()==h['after']
 result={'source':str(source),'output':str(destination),'blocks':blocks,'changed_files':list(changed),'unchanged_files':preserved,'Whiteout_Visit_files_preserved':datapacks,'omitted':['session.lock'],'sha256':hashlib.sha256(destination.read_bytes()).hexdigest(),'files':hashes}
 Path(report).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps({k:v for k,v in result.items() if k not in ('files','blocks')},ensure_ascii=False,indent=2))
if __name__=='__main__':main(*sys.argv[1:])
