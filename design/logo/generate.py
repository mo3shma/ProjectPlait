import math,sys
def mix(a,b,t): return tuple(int(a[i]*(1-t)+b[i]*t) for i in range(3))
def hexc(c): return '#%02X%02X%02X'%c
def parse(h): h=h.lstrip('#'); return tuple(int(h[i:i+2],16) for i in (0,2,4))
cols=[parse('#7C8BFF'),parse('#FF8F7A'),parse('#FFD166')]
dark=parse('#10122E')
amp,turns,w,y0,y1=[float(x) for x in sys.argv[1:6]]; out=sys.argv[6]; mode=sys.argv[7]; withbg=(mode=='bg')
N=240; cx=54
def run_paths():
    back=[];front=[]
    for i in range(3):
        pts=[]
        for k in range(N+1):
            t=k/N; ph=2*math.pi*(t*turns+i/3)
            pts.append((cx+amp*math.sin(ph), y0+(y1-y0)*t, math.cos(ph)))
        cur=[pts[0]]
        for p in pts[1:]:
            if (p[2]>0)==(cur[-1][2]>0): cur.append(p)
            else:
                cur.append(p)   # share the boundary point
                (front if cur[len(cur)//2][2]>0 else back).append((i,cur)); cur=[p]
        (front if cur[len(cur)//2][2]>0 else back).append((i,cur))
    return back,front
def d(pts): return 'M'+'L'.join(f'{x:.2f} {y:.2f}' for x,y,_ in pts)
back,front=run_paths()
o=[]
for i,p in back:
    c=mix(cols[i],dark,0.5)
    if mode=='mono':
        o.append(f'<path d="{d(p)}" stroke="#FFFFFF" stroke-opacity="0.45" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round" fill="none"/>'); continue
    o.append(f'<path d="{d(p)}" stroke="{hexc(c)}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round" fill="none"/>')
for i,p in front:
    if mode=='mono':
        o.append(f'<path d="{d(p)}" stroke="#FFFFFF" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round" fill="none"/>'); continue
    o.append(f'<path d="{d(p)}" stroke="#10122E" stroke-opacity="0.7" stroke-width="{w+2.6}" stroke-linecap="round" stroke-linejoin="round" fill="none"/>')
    o.append(f'<path d="{d(p)}" stroke="{hexc(cols[i])}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round" fill="none"/>')
bg='<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1D2260"/><stop offset="1" stop-color="#0E1030"/></linearGradient></defs><rect width="108" height="108" rx="24" fill="url(#bg)"/>' if withbg else ''
open(out,'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">{bg}\n'+'\n'.join(o)+'\n</svg>')
