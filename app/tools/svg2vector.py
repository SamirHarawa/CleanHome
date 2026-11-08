# Convert simple SVG icons (path/rect/circle, fill or stroke) to Android vector drawables.
import sys, re, os, xml.etree.ElementTree as ET
def rect(e):
    x,y,w,h=(float(e.get(k,0)) for k in ('x','y','width','height')); r=float(e.get('rx',0))
    if not r: return f"M{x},{y}h{w}v{h}h{-w}z"
    return (f"M{x+r},{y}h{w-2*r}a{r},{r} 0 0 1 {r},{r}v{h-2*r}a{r},{r} 0 0 1 {-r},{r}"
            f"h{-(w-2*r)}a{r},{r} 0 0 1 {-r},{-r}v{-(h-2*r)}a{r},{r} 0 0 1 {r},{-r}z")
def circle(e):
    cx,cy,r=(float(e.get(k)) for k in ('cx','cy','r'))
    return f"M{cx-r},{cy}a{r},{r} 0 1 0 {2*r},0a{r},{r} 0 1 0 {-2*r},0z"
src,dst=sys.argv[1],sys.argv[2]
root=ET.parse(src).getroot(); ns='{http://www.w3.org/2000/svg}'
vb=[float(v) for v in root.get('viewBox').split()]
stroke=root.get('stroke') not in (None,'none'); sw=root.get('stroke-width','1.6')
paths=[]
for e in root.iter():
    t=e.tag.replace(ns,'')
    if t=='path': d=e.get('d')
    elif t=='rect': d=rect(e)
    elif t=='circle': d=circle(e)
    else: continue
    paths.append(d)
w,h=vb[2],vb[3]
out=[f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{w*10:g}dp" android:height="{h*10:g}dp" android:viewportWidth="{w:g}" android:viewportHeight="{h:g}">',
     f'<group android:translateX="{-vb[0]:g}" android:translateY="{-vb[1]:g}">']
for d in paths:
    if stroke:
        out.append(f'<path android:pathData="{d}" android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="{sw}" android:strokeLineCap="round" android:strokeLineJoin="round"/>')
    else:
        out.append(f'<path android:pathData="{d}" android:fillColor="#FFFFFFFF"/>')
out.append('</group></vector>')
open(dst,'w').write('\n'.join(out))
