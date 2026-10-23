#!/usr/bin/env python3
"""Install and exercise the APK on an already booted emulator/device via adb."""
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get('ELECTROGAS_ADB', 'adb')
ROOT = Path(__file__).resolve().parents[1]
UI_CACHE = None

def adb(*args, timeout=60):
    return subprocess.check_output([ADB, *args], timeout=timeout).decode('utf-8', errors='replace')

def dump():
    global UI_CACHE
    # Never read a previous dump when Android is transitioning between windows.
    for _ in range(4):
        adb('shell', 'rm', '-f', '/sdcard/electrogas-ui.xml')
        adb('shell', 'uiautomator', 'dump', '/sdcard/electrogas-ui.xml')
        try:
            UI_CACHE = ET.fromstring(adb('shell', 'cat', '/sdcard/electrogas-ui.xml'))
            return UI_CACHE
        except (ET.ParseError, subprocess.CalledProcessError):
            time.sleep(1)
    raise AssertionError('Android did not provide a current UI hierarchy')

def tap(label, description=False):
    global UI_CACHE
    tree = UI_CACHE if UI_CACHE is not None else dump()
    for n in tree.iter('node'):
        if n.get('content-desc' if description else 'text','').casefold() == label.casefold():
            x1,y1,x2,y2 = map(int, re.findall(r'\d+', n.get('bounds')))
            adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            UI_CACHE = None
            time.sleep(.8)
            return
    raise AssertionError('Missing element: '+label)

def text_contains(value):
    global UI_CACHE
    for _ in range(4):
        tree = UI_CACHE if UI_CACHE is not None else dump()
        texts = [''.join(n.get('text','').split()) for n in tree.iter('node')]
        if ''.join(value.split()) in texts:
            return
        UI_CACHE = None
        time.sleep(1)
    raise AssertionError((value, texts))

def set_new_index(value):
    tap('Nouvel index · kWh', True)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'keyevent', *(['KEYCODE_DEL']*12))
    adb('shell', 'input', 'text', value)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')

print(adb('install','--no-streaming','-r',str(ROOT/'dist/ElectroGas.apk'),timeout=180))
adb('shell','pm','clear','dz.electrogas')
adb('logcat','-c')
adb('shell','am','start','-W','-n','dz.electrogas/.MainActivity')
time.sleep(2)
text_contains('3 157,01 DA')
(ROOT/'build/default-screen.png').write_bytes(subprocess.check_output([ADB,'exec-out','screencap','-p']))
tap('Gaz');text_contains('02  /  Gaz')
tap('Frais');text_contains('03  /  Frais et ajustements')
tap('Total');text_contains('04  /  Votre estimation');text_contains('3 157,01 DA')
tap('Électricité');set_new_index('55331');text_contains('3 178,34 DA')
set_new_index('1');text_contains('— DA')
tap('Total');text_contains('Vérifiez votre saisie');tap('Corriger')
set_new_index('55331')
adb('shell','am','force-stop','dz.electrogas')
UI_CACHE = None
adb('shell','am','start','-W','-n','dz.electrogas/.MainActivity')
time.sleep(1);text_contains('3 178,34 DA');text_contains('55331')
assert 'FATAL EXCEPTION' not in adb('logcat','-d','-s','AndroidRuntime:E')
print('PASS: install, launch, four pages, workbook total, recalculation, invalid readings, persistence; no AndroidRuntime crash.')
