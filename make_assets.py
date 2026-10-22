from pathlib import Path
import json,numpy as np, wave,subprocess,tempfile
R=Path(__file__).parent/'src/main/resources';A=R/'assets/whiteout'
def write(p,o):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n')
def box(lo,hi,tex):return {'from':lo,'to':hi,'faces':{f:{'texture':'#'+tex} for f in ['north','south','east','west','up','down']}}
textures={'metal':'minecraft:block/iron_block','dark':'minecraft:block/gray_concrete','black':'minecraft:block/black_concrete','tank':'minecraft:block/red_terracotta','wood':'minecraft:block/spruce_planks','green':'minecraft:block/lime_concrete','red':'minecraft:block/red_concrete','light':'minecraft:block/yellow_concrete'}
def model(name,e,tex=textures):
 write(A/f'models/block/{name}.json',{'parent':'minecraft:block/block','textures':dict(tex,particle=tex.get('metal',next(iter(tex.values())))),'elements':e,'display':{'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.72,.72,.72]},'ground':{'translation':[0,2,0],'scale':[.4,.4,.4]},'fixed':{'rotation':[0,180,0],'scale':[.6,.6,.6]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.5,.5,.5]}}})
# Tube frame, engine, red fuel tank, starter handle, exhaust and control face.
e=[box([2,1,2],[14,3,14],'dark'),box([4,3,5],[12,9,13],'dark'),box([3,10,3],[13,13,12],'tank'),box([9,13,6],[11,14,8],'black'),box([5,4,2],[11,9,4],'metal'),box([12,4,8],[14,10,10],'black'),box([12,9,10],[14,11,14],'dark'),box([5,6,1],[6,8,2],'black'),box([8,6,1],[10,7,2],'light')]
for x in [1,14]:
 for z in [1,14]:e.append(box([x,1,z],[x+1,14,z+1],'metal'))
 e.append(box([x,13,1],[x+1,14,15],'metal'))
for z in [1,14]:e.append(box([1,1,z],[15,2,z+1],'metal'))
for y in [4,5.5,7,8.5]:e.append(box([3.5,y,5],[4,y+.5,12],'black'))
for x in [1,12]:e.append(box([x,0,10],[x+3,4,14],'black'))
for active in [False,True]:model('generator'+('_on' if active else ''),e+[box([7,8,1],[8,9,2],'green' if active else 'red')])
# Wall box, hinge, handle and indicator; separate visible fuse state handled by renderer model state.
e=[box([3,2,12],[13,15,16],'dark'),box([3.5,2.5,11.5],[12.5,14.5,12],'metal'),box([4,4,11],[7,10,11.5],'black'),box([9,4,10.8],[10,8,11.5],'dark'),box([8,11,11],[11,13,11.5],'light')]
for active in [False,True]:model('fuse_panel'+('_on' if active else ''),e+[box([5,12,10.9],[6,13,11.5],'green' if active else 'red')])
e=[]
for x in [0,15]:
 for z in [3,12]:e.append(box([x,0,z],[x+1,16,z+1],'dark'))
for y in [1,6,11]:e.append(box([1.02,y,3.02],[14.98,y+1,12.98],'wood'));e.append(box([1.02,y,2.875],[14.98,y+.6,2.99],'metal'))
e.append(box([1.02,15,12],[14.98,16,13],'dark'));model('storage_shelf',e)
for n in ['generator','fuse_panel','storage_shelf']:
 variants={}
 for facing,rot in [('north',0),('east',90),('south',180),('west',270)]:
  for active in [False,True]:variants[f'facing={facing},active={str(active).lower()}']={'model':'whiteout:block/'+n+('_on' if active and n!='storage_shelf' else ''),'y':rot}
 write(A/f'blockstates/{n}.json',{'variants':variants});write(A/f'models/item/{n}.json',{'parent':'whiteout:block/'+n})
 write(R/f'data/whiteout/loot_table/blocks/{n}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'whiteout:'+n}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for name,colour in [('fuel_canister','tank'),('empty_canister','dark')]:
 e=[box([4,1,5],[12,11,11],colour),box([4,11,5],[5,15,7],'metal'),box([11,11,5],[12,15,7],'metal'),box([4,14,5],[12,15,7],'metal'),box([9,11,9],[12,13,11],'black'),box([7,4,4.8],[9,8,5],'light')]
 model(name,e);write(A/f'models/item/{name}.json',{'parent':'whiteout:block/'+name})
model('fuse',[box([6,2,6],[10,14,10],'light'),box([5.5,1,5.5],[10.5,4,10.5],'metal'),box([5.5,12,5.5],[10.5,15,10.5],'metal')]);write(A/'models/item/fuse.json',{'parent':'whiteout:block/fuse'})
ru={'block.whiteout.generator':'Бензиновый генератор','block.whiteout.fuse_panel':'Электрощиток','block.whiteout.storage_shelf':'Стеллаж','item.whiteout.fuel_canister':'Канистра топлива','item.whiteout.empty_canister':'Пустая канистра','item.whiteout.fuse':'Предохранитель','itemGroup.whiteout.power_objects':'Whiteout — электричество и хранение','message.whiteout.shelf_occupied':'Место занято. Забери предмет пустой рукой.','message.whiteout.fuse_inserted':'Предохранитель установлен','message.whiteout.fuse_missing':'Нужен предохранитель','message.whiteout.panel_on':'Питание включено','message.whiteout.panel_no_power':'Нет питания: запусти генератор в радиусе 32 блоков','message.whiteout.tank_full':'В бак не помещается ещё одна канистра','message.whiteout.fuel_added':'Заправлено. Топлива на %s мин.','message.whiteout.generator_off':'Генератор остановлен','message.whiteout.generator_on':'Генератор работает. Осталось около %s мин. Shift + ПКМ — выключить','message.whiteout.fuel_missing':'Нужна канистра топлива','message.whiteout.starter_busy':'Другой игрок уже запускает генератор','screen.whiteout.starter':'ЗАПУСК ГЕНЕРАТОРА','screen.whiteout.pull':'Дёрнуть стартер [Пробел]','screen.whiteout.instruction':'Дёрни, когда указатель в зелёной зоне','screen.whiteout.progress':'Удачные рывки: %s / %s','screen.whiteout.miss':'Не схватил. Попробуй ещё раз.'}
en={'block.whiteout.generator':'Fuel Generator','block.whiteout.fuse_panel':'Fuse Panel','block.whiteout.storage_shelf':'Storage Shelf','item.whiteout.fuel_canister':'Fuel Canister','item.whiteout.empty_canister':'Empty Canister','item.whiteout.fuse':'Fuse','itemGroup.whiteout.power_objects':'Whiteout — Power & Storage','message.whiteout.shelf_occupied':'Slot occupied. Use an empty hand to retrieve the item.','message.whiteout.fuse_inserted':'Fuse installed','message.whiteout.fuse_missing':'A fuse is required','message.whiteout.panel_on':'Power connected','message.whiteout.panel_no_power':'No power: start a generator within 32 blocks','message.whiteout.tank_full':'Not enough room for another canister','message.whiteout.fuel_added':'Refuelled. Fuel for %s min.','message.whiteout.generator_off':'Generator stopped','message.whiteout.generator_on':'Running. About %s min left. Sneak + use to stop','message.whiteout.fuel_missing':'A fuel canister is required','message.whiteout.starter_busy':'Another player is starting this generator','screen.whiteout.starter':'START GENERATOR','screen.whiteout.pull':'Pull starter [Space]','screen.whiteout.instruction':'Pull when the marker is in the green zone','screen.whiteout.progress':'Successful pulls: %s / %s','screen.whiteout.miss':'Did not catch. Try again.'}
for lang,d in [('ru_ru',ru),('en_us',en)]:
 lang_path=A/f'lang/{lang}.json'
 current=json.loads(lang_path.read_text(encoding='utf-8')) if lang_path.exists() else {}
 for k,v in d.items():current.setdefault(k,v)
 write(lang_path,current)
# Original synthesized mechanical sounds; no borrowed game audio.
rate=44100;rng=np.random.default_rng(340)
def save(name,data):
 out=A/'sounds'/f'{name}.ogg';out.parent.mkdir(parents=True,exist_ok=True)
 data=np.clip(data,-.98,.98);wav=out.with_suffix('.wav')
 with wave.open(str(wav),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes((data*32767).astype('<i2').tobytes())
 subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','4',str(out)],check=True);wav.unlink()
t=np.arange(rate*4)/rate
engine=(.28*np.sin(2*np.pi*48*t)+.13*np.sin(2*np.pi*96*t)+.07*np.sin(2*np.pi*192*t))*(.7+.3*np.sin(2*np.pi*24*t)**4)
# periodic fine mechanical rasp for seamless looping
engine+=.045*np.sin(2*np.pi*731*t)*np.sin(2*np.pi*24*t)**8
save('generator_engine',engine)
for name,dur in [('generator_pull',.45),('generator_start',1.2),('generator_stop',.8),('generator_fail',.65),('fuse_click',.12)]:
 t=np.arange(int(rate*dur))/rate;env=np.sin(np.pi*t/dur)**2
 if name=='generator_pull':data=(.25*rng.normal(0,1,len(t))+.15*np.sin(2*np.pi*(350*t-180*t*t)))*env
 elif name=='fuse_click':data=(.32*rng.normal(0,1,len(t))+.2*np.sin(2*np.pi*1800*t))*np.exp(-50*t)
 else:
  hz=30+65*t/dur if name=='generator_start' else 95-70*t/dur
  phase=2*np.pi*np.cumsum(hz)/rate;data=(.4*np.sin(phase)+.1*rng.normal(0,1,len(t)))*env
 save(name,data)
# Preserve bundled personal music definitions when regenerating generator sound effects.
sound_file=A/'sounds.json'
registered=json.loads(sound_file.read_text(encoding='utf-8')) if sound_file.exists() else {}
registered.update({n:{'sounds':[{'name':'whiteout:'+n,'stream':False}]} for n in ['generator_engine','generator_pull','generator_start','generator_stop','generator_fail','fuse_click']})
write(sound_file,registered)
print('Assets generated: models, locales, loot and original sounds')
