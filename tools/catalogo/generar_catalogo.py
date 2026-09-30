#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Genera el catálogo de ejercicios que trae la app de fábrica.

Fuentes (licencias abiertas):
  - Ejercicios curados en español (tools/catalogo/curado.py, propios).
  - free-exercise-db (https://github.com/yuhonas/free-exercise-db, dominio público / Unlicense).

Salida:
  app/src/main/assets/catalog/exercises.json
  app/src/main/assets/catalog/img/<id>.webp  (solo ejercicios curados, para verlos sin internet)

Uso:  python3 tools/catalogo/generar_catalogo.py [--sin-imagenes]
Requiere: pillow (pip install pillow) para las imágenes.
"""
import io
import json
import os
import sys
import time
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
from curado import EJERCICIOS, IMAGEN_SUSTITUTA, P, M, E, L, Z  # noqa: E402

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SALIDA = os.path.join(RAIZ, "app", "src", "main", "assets", "catalog")
FEDB_JSON = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
FEDB_IMG = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
CACHE = os.path.join(os.path.dirname(__file__), ".cache")

FEDB_MUSCLE = {
    "abdominals": "ABS", "abductors": "ABDUCTORS", "adductors": "ADDUCTORS", "biceps": "BICEPS",
    "calves": "CALVES", "chest": "CHEST", "forearms": "FOREARMS", "glutes": "GLUTES",
    "hamstrings": "HAMSTRINGS", "lats": "LATS", "lower back": "LOWER_BACK", "middle back": "MIDDLE_BACK",
    "neck": "NECK", "quadriceps": "QUADS", "shoulders": "SHOULDERS", "traps": "TRAPS", "triceps": "TRICEPS",
}
FEDB_EQUIP = {
    "barbell": "BARBELL", "dumbbell": "DUMBBELL", "body only": "NONE", "cable": "CABLE", "machine": "MACHINE",
    "kettlebells": "KETTLEBELL", "bands": "BANDS", "medicine ball": "OTHER", "exercise ball": "OTHER",
    "foam roll": "OTHER", "other": "OTHER", "e-z curl bar": "EZ_BAR", None: "NONE",
}
# Ejercicios que usan el propio cuerpo como carga aunque necesiten una barra o paralelas.
SIN_CARGA = {
    "remo-invertido", "dominadas", "dominadas-supinas", "dominadas-asistidas", "fondos-pecho", "fondos-banco",
    "elevacion-piernas-colgado", "hiperextensiones",
}
FEDB_LEVEL = {"beginner": "BEGINNER", "intermediate": "INTERMEDIATE", "expert": "ADVANCED"}


def descargar(url, destino):
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    if os.path.exists(destino) and os.path.getsize(destino) > 0:
        return destino
    for intento in range(5):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                datos = r.read()
            with open(destino, "wb") as f:
                f.write(datos)
            return destino
        except Exception:  # noqa: BLE001 - reintentar ante cortes de red
            if intento == 4:
                raise
            time.sleep(2 ** intento)
    return destino


def lista(texto, tabla):
    return [tabla[t] for t in texto.split()] if texto else []


def patron_fedb(x):
    nombre = x["name"].lower()
    cat = x.get("category")
    prim = x["primaryMuscles"][0] if x["primaryMuscles"] else ""
    comp = x.get("mechanic") == "compound"
    fuerza = x.get("force")
    if cat == "cardio":
        return "CARDIO"
    if cat == "stretching":
        return "MOBILITY"
    if prim == "abdominals":
        return "CORE"
    if prim == "quadriceps":
        if any(k in nombre for k in ("lunge", "step", "split", "single-leg", "one leg", "one-leg", "pistol")):
            return "LUNGE"
        return "SQUAT" if comp else "ISOLATION"
    if prim in ("hamstrings", "glutes", "lower back"):
        if "curl" in nombre or not comp:
            return "ISOLATION"
        return "HINGE"
    if prim == "chest":
        return "HORIZONTAL_PUSH" if comp and fuerza == "push" else "ISOLATION"
    if prim == "shoulders":
        if comp and fuerza == "push":
            return "VERTICAL_PUSH"
        return "ISOLATION"
    if prim == "lats":
        if not comp:
            return "ISOLATION"
        return "HORIZONTAL_PULL" if "row" in nombre else "VERTICAL_PULL"
    if prim == "middle back":
        return "HORIZONTAL_PULL" if comp else "ISOLATION"
    if prim == "triceps" and comp and fuerza == "push":
        return "HORIZONTAL_PUSH"
    return "ISOLATION"


def contraindicaciones_fedb(x, patron, equipo):
    z = set()
    nombre = x["name"].lower()
    if patron in ("SQUAT", "LUNGE"):
        z.add("KNEE")
    if patron == "HINGE" and "BARBELL" in equipo:
        z.add("LOWER_BACK")
    if patron == "VERTICAL_PUSH":
        z.add("SHOULDER")
    if x.get("category") in ("plyometrics", "olympic weightlifting"):
        z.update({"KNEE", "ANKLE", "LOWER_BACK"})
    if "behind the neck" in nombre or "behind neck" in nombre:
        z.update({"SHOULDER", "NECK"})
    if "neck" in nombre:
        z.add("NECK")
    return sorted(z)


def equipo_fedb(x):
    eq = {FEDB_EQUIP.get(x.get("equipment"), "OTHER")}
    nombre = x["name"].lower()
    if "bench" in nombre:
        eq.add("BENCH")
    if any(k in nombre for k in ("pull-up", "pullup", "chin-up", "chin up", "hanging")):
        eq.add("PULLUP_BAR")
    eq.discard("NONE")
    return sorted(eq)


def imagen_local(fedb_id, ej_id, fedb_por_id, con_imagenes):
    if not con_imagenes or not fedb_id or fedb_id not in fedb_por_id:
        return None
    imgs = fedb_por_id[fedb_id].get("images") or []
    if not imgs:
        return None
    from PIL import Image  # noqa: WPS433
    ruta_cache = descargar(FEDB_IMG + imgs[0], os.path.join(CACHE, "img", imgs[0]))
    img = Image.open(ruta_cache).convert("RGB")
    ancho = 360
    alto = int(img.height * ancho / img.width)
    img = img.resize((ancho, alto))
    os.makedirs(os.path.join(SALIDA, "img"), exist_ok=True)
    destino = os.path.join(SALIDA, "img", ej_id + ".webp")
    img.save(destino, "WEBP", quality=68, method=6)
    return "catalog/img/" + ej_id + ".webp"


def main():
    con_imagenes = "--sin-imagenes" not in sys.argv
    fedb = json.load(open(descargar(FEDB_JSON, os.path.join(CACHE, "exercises.json")), encoding="utf-8"))
    fedb_por_id = {x["id"]: x for x in fedb}
    usados = set()
    salida = []
    ids = set()

    for rango, (ej_id, fedb_id, nombre, pat, prim, sec, eq, niv, contra, comp, uni, tiempo, pasos, consejo) in enumerate(EJERCICIOS):
        assert ej_id not in ids, "id duplicado: " + ej_id
        ids.add(ej_id)
        assert len(pasos) == 3, ej_id
        if fedb_id:
            assert fedb_id in fedb_por_id, "no existe en free-exercise-db: " + fedb_id
            usados.add(fedb_id)
        equipo = sorted({E[e] for e in eq.split()} - {"NONE"})
        img_id = fedb_id or IMAGEN_SUSTITUTA.get(ej_id)
        if img_id:
            assert img_id in fedb_por_id, "imagen sustituta inexistente: " + img_id
        urls = [FEDB_IMG + i for i in fedb_por_id[img_id].get("images", [])] if img_id else []
        salida.append({
            "id": ej_id,
            "name": nombre,
            "nameEn": fedb_por_id[fedb_id]["name"] if fedb_id else None,
            "pattern": P[pat],
            "primaryMuscles": lista(prim, M),
            "secondaryMuscles": lista(sec, M),
            "equipment": equipo,
            "level": L[niv],
            "contraindications": sorted(lista(contra, Z)),
            "compound": bool(comp),
            "steps": pasos,
            "tip": consejo,
            "image": imagen_local(img_id, ej_id, fedb_por_id, con_imagenes),
            "imageUrls": urls,
            "curated": True,
            "unilateral": bool(uni),
            "timed": bool(tiempo),
            "source": "warriors-box" if not fedb_id else "warriors-box+free-exercise-db",
            "rank": rango,
            "loadable": ej_id not in SIN_CARGA,
        })

    for x in fedb:
        if x["id"] in usados or not x["primaryMuscles"]:
            continue
        patron = patron_fedb(x)
        equipo = equipo_fedb(x)
        ej_id = "fedb-" + x["id"].lower()
        if ej_id in ids:
            continue
        ids.add(ej_id)
        salida.append({
            "id": ej_id,
            "name": x["name"],
            "nameEn": x["name"],
            "pattern": patron,
            "primaryMuscles": [FEDB_MUSCLE[m] for m in x["primaryMuscles"] if m in FEDB_MUSCLE] or ["ABS"],
            "secondaryMuscles": [FEDB_MUSCLE[m] for m in x.get("secondaryMuscles", []) if m in FEDB_MUSCLE],
            "equipment": equipo,
            "level": FEDB_LEVEL.get(x.get("level"), "INTERMEDIATE"),
            "contraindications": contraindicaciones_fedb(x, patron, equipo),
            "compound": x.get("mechanic") == "compound",
            "steps": x.get("instructions", []),
            "tip": None,
            "image": None,
            "imageUrls": [FEDB_IMG + i for i in x.get("images", [])],
            "curated": False,
            "unilateral": any(k in x["name"].lower() for k in ("one-arm", "one arm", "single", "one-leg", "one leg", "alternat")),
            "timed": x.get("category") in ("cardio", "stretching"),
            "source": "free-exercise-db",
        })

    os.makedirs(SALIDA, exist_ok=True)
    doc = {
        "version": 1,
        "sources": [
            {"name": "Warriors Box (curado en español)", "license": "Propio"},
            {"name": "free-exercise-db", "url": "https://github.com/yuhonas/free-exercise-db", "license": "Unlicense (dominio público)"},
        ],
        "exercises": salida,
    }
    with open(os.path.join(SALIDA, "exercises.json"), "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    print("Ejercicios:", len(salida), "curados:", sum(1 for s in salida if s["curated"]))


if __name__ == "__main__":
    main()
