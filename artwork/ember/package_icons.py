"""Resize generated Ember artwork into Android launcher assets (Pillow required)."""
from pathlib import Path
from PIL import Image
ROOT = Path(__file__).resolve().parents[2]
source = Image.open(Path(__file__).with_name('ember-source.png')).convert('RGBA')
alpha = source.getchannel('A')
assert alpha.getextrema()[0] == 0, 'Expected transparent source'
mark = source.crop(alpha.getbbox())

def fit(size, fraction, background):
    canvas = Image.new('RGBA', (size, size), background)
    logo = mark.copy()
    logo.thumbnail((round(size*fraction), round(size*fraction)), Image.Resampling.LANCZOS)
    canvas.alpha_composite(logo, ((size-logo.width)//2, (size-logo.height)//2))
    return canvas

for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    directory = ROOT / 'app/src/main/res' / f'mipmap-{density}'
    fit(size, .78, '#0B0B0E').save(directory / 'ic_launcher.png')
    fit(round(size*108/48), .60, (0,0,0,0)).save(directory / 'ic_launcher_foreground.png')
fit(512, .78, '#0B0B0E').save(Path(__file__).with_name('ember-preview.png'))
print('Packaged 10 launcher assets and preview')
