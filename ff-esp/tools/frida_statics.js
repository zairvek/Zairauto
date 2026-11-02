/**
 * frida_statics.js — encuentra punteros estáticos en tiempo de ejecución.
 *
 * Prerequisitos en PC:
 *   pip install frida-tools
 *   adb push frida-server-XX-android-arm64 /data/local/tmp/frida-server
 *   adb shell "chmod 755 /data/local/tmp/frida-server && /data/local/tmp/frida-server &"
 *
 * Uso (con el juego ya abierto):
 *   frida -U -n "Free Fire" -l frida_statics.js
 *
 * Output: offsets listos para copiar en Offsets.kt
 */

"use strict";

var BASE = Module.getBaseAddress("libGameAssembly.so");

// ── IL2CPP API ─────────────────────────────────────────────────────────────
function exp(name) {
    var addr = Module.findExportByName("libGameAssembly.so", name);
    if (!addr) throw new Error("export not found: " + name);
    return addr;
}

var il2cpp = {
    domainGet:            new NativeFunction(exp("il2cpp_domain_get"),             'pointer',[]),
    domainGetAssemblies:  new NativeFunction(exp("il2cpp_domain_get_assemblies"),  'pointer',['pointer','pointer']),
    assemblyGetImage:     new NativeFunction(exp("il2cpp_assembly_get_image"),     'pointer',['pointer']),
    imageGetClassName:    new NativeFunction(exp("il2cpp_image_get_class_count"),  'uint32', ['pointer']),
    imageGetClass:        new NativeFunction(exp("il2cpp_image_get_class"),        'pointer',['pointer','uint32']),
    classGetName:         new NativeFunction(exp("il2cpp_class_get_name"),         'pointer',['pointer']),
    classGetFields:       new NativeFunction(exp("il2cpp_class_get_fields"),       'pointer',['pointer','pointer']),
    fieldGetName:         new NativeFunction(exp("il2cpp_field_get_name"),         'pointer',['pointer']),
    fieldGetOffset:       new NativeFunction(exp("il2cpp_field_get_offset"),       'int32',  ['pointer']),
    fieldIsStatic:        function(field) {
        // static fields have offset < 0 in some IL2CPP versions; check attributes
        // Simpler: offset returned by il2cpp_field_get_offset for static = 0xFFFF... or offset in static block
        return il2cpp.fieldGetOffset(field) < 0;
    },
    classGetStaticData:   new NativeFunction(exp("il2cpp_class_get_static_field_data"),'pointer',['pointer']),
    fromName:             new NativeFunction(exp("il2cpp_class_from_name"),        'pointer',['pointer','pointer','pointer']),
};

// ── helpers ────────────────────────────────────────────────────────────────
function toHex(ptr) { return "0x" + ptr.sub(BASE).toString(16).toUpperCase(); }

function getFields(klass) {
    var out = []; var iter = Memory.alloc(8); iter.writePointer(ptr("0x0"));
    var f;
    while (!(f = il2cpp.classGetFields(klass, iter)).isNull()) {
        var name   = il2cpp.fieldGetName(f).readCString();
        var offset = il2cpp.fieldGetOffset(f);
        out.push({ name, offset, ptr: f });
    }
    return out;
}

function findClass(image, name) {
    return il2cpp.fromName(image, Memory.allocUtf8String(""), Memory.allocUtf8String(name));
}

// ── target classes ──────────────────────────────────────────────────────────
var TARGET_CLASSES = [
    // Player manager candidates
    "FightingFighterManager","BattleRoyalePlayerManager","FighterManager",
    // Player class candidates
    "FightingFighter","BattleCharacter","BaseCharacter","CharacterState",
    // Camera controller candidates
    "BaseCameraController","ThirdPersonCameraController","CameraController",
];

function run() {
    var domain     = il2cpp.domainGet();
    var countBuf   = Memory.alloc(4);
    var assemblies = il2cpp.domainGetAssemblies(domain, countBuf);
    var asmCount   = countBuf.readU32();

    // Find Assembly-CSharp image
    var image = null;
    for (var i = 0; i < asmCount; i++) {
        var asm = assemblies.add(i * Process.pointerSize).readPointer();
        var img = il2cpp.assemblyGetImage(asm);
        // il2cpp_image_get_name not always exported; just iterate all images
        if (!img.isNull()) {
            // Try to find one of our targets
            for (var t of TARGET_CLASSES) {
                var klass = findClass(img, t);
                if (!klass.isNull()) { image = img; break; }
            }
        }
        if (image) break;
    }
    if (!image) { console.log("[-] Assembly-CSharp image not found"); return; }
    console.log("[+] image found");

    console.log("\n===== OFFSETS FOR Offsets.kt =====\n");

    for (var target of TARGET_CLASSES) {
        var klass = findClass(image, target);
        if (klass.isNull()) continue;

        var staticData = il2cpp.classGetStaticData(klass);
        console.log("// class: " + target);
        if (!staticData.isNull()) {
            console.log("//   StaticFields ptr (runtime): " + staticData);
            // Print fields
            for (var f of getFields(klass)) {
                if (f.offset < 0 || f.offset > 0x10000) continue; // skip statics with weird offsets
                // Print all fields so user can identify by name
            }
        }

        // Print instance fields
        var fields = getFields(klass);
        if (fields.length > 0) {
            console.log("//   instance fields:");
            for (var f of fields) {
                if (f.offset >= 0 && f.offset < 0x1000)
                    console.log("//     " + f.name + ": 0x" + f.offset.toString(16));
            }
        }

        // If it has a singleton, try to read the instance pointer
        if (!staticData.isNull()) {
            try {
                var firstPtr = staticData.readPointer();
                if (!firstPtr.isNull())
                    console.log("//   singleton instance (at sf+0x0): " + firstPtr);
            } catch(_) {}
        }
        console.log("");
    }

    console.log("===== END =====");
    console.log("\nTypeInfo libBase offsets:");
    for (var target of TARGET_CLASSES) {
        var klass = findClass(image, target);
        if (!klass.isNull())
            console.log("  " + target + " TypeInfo: " + toHex(klass));
    }
}

// Run after a short delay to ensure IL2CPP is initialized
setTimeout(run, 3000);
