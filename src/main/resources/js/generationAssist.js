async function getIdMaps(){
  var idMaps = new Set()
  var components = document.getElementsByName('selected')
  components.forEach((component, index) => {
    try {
        if(component.checked){
            idMaps.add(component.id)
        }
    } catch(error){
        console.error('Failed to copy: ', error);
    }
  });
  return convertSetToString(idMaps)
}

async function showIdMaps(){
    var idMaps = await getIdMaps()
    var mapTextElement = document.getElementById("idMapText");
    if (mapTextElement) { mapTextElement.value = idMaps}
}

async function setIdMaps(){
    var chooseGeneralComponentApplyingElement = document.getElementById("chooseGeneralComponentApplyingId")
    var chooseGeneralComponentAppliedElement = document.getElementById("chooseGeneralComponentAppliedId")
    if (chooseGeneralComponentApplyingElement) { chooseGeneralComponentApplyingElement.style.display = 'block' }
    if (chooseGeneralComponentAppliedElement) { chooseGeneralComponentAppliedElement.style.display = 'none'}

    var currentValue = document.getElementById('idMapText').value
    var setWithChecked = await convertStringToSet(currentValue)
    var components = document.getElementsByName('selected')
    components.forEach((component, index) => {
        try {
            if(component.id && setWithChecked.has(component.id)){
                component.checked = true
            } else {
                component.checked = false
            }
        } catch(error){
            console.error('Failed to copy: ', error);
        }
    });


    setTimeout(function() {
        if (chooseGeneralComponentApplyingElement) { chooseGeneralComponentApplyingElement.style.display = 'none' }
        if (chooseGeneralComponentAppliedElement) { chooseGeneralComponentAppliedElement.style.display = 'block' }
    }, 1000);

}


async function convertSetToString(setIdMaps){
    return compressString(Array.from(setIdMaps).join(','), { encoding: 'gzip', output: 'base64'})
}

async function convertStringToSet(hex){
    return new Set((await decompressString(hex, { encoding: 'gzip', output: 'base64'})).split(','))
}

async function copyToClipboard() {
    try {
        var mapTextElement = document.getElementById("idMapText");
        if (mapTextElement) {
            await navigator.clipboard.writeText(mapTextElement.value)
        }
    } catch (error) {
        console.error('Failed to copy: ', error)
    }
}

document.addEventListener("DOMContentLoaded", function() {
    var chooseComponentButtonElement = document.getElementById('chooseComponentButtonId')
    var mapTextElement = document.getElementById('idMapText')
    var mapSelectElement = document.getElementById('idMapSelect')
    var mapDivElement = document.getElementById('idMapDiv')
    showIdMaps()

    if (chooseComponentButtonElement) {
        chooseComponentButtonElement.addEventListener('click', function(event) {
            event.preventDefault();
            document.getElementById('categories_dialog').showModal();
            return false;
        })
    }
    if (mapTextElement) {
        mapTextElement.addEventListener('keydown', function(event) {
            if (event.key === 'Enter') {
              event.preventDefault();
              setIdMaps()
            }
        });
    }

    if (mapSelectElement && mapDivElement) {
        mapSelectElement.addEventListener('change', function(event) {
            event.preventDefault();
            if (mapSelectElement.value == "custom"){
                mapDivElement.style.display = 'flex';
            }else{
                mapDivElement.style.display = 'none';
                mapTextElement.value=mapSelectElement.value
                setIdMaps()
            }
        })
    }
});

// Utility: convert ArrayBuffer to Base64 string
function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

// Utility: convert Base64 string to ArrayBuffer
function base64ToArrayBuffer(base64) {
  const binary_string = atob(base64);
  const len = binary_string.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binary_string.charCodeAt(i);
  }
  return bytes.buffer;
}

// Utility: convert ArrayBuffer to hex string
function arrayBufferToHex(buffer) {
  const bytes = new Uint8Array(buffer);
  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

// Utility: convert hex string to ArrayBuffer
function hexToArrayBuffer(hex) {
  const len = hex.length;
  const bytes = new Uint8Array(len / 2);
  for (let i = 0; i < len; i += 2) {
    bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16);
  }
  return bytes.buffer;
}

// Compress string (UTF-8) → gzip → base64 or hex
async function compressString(s, { encoding = 'gzip', output = 'base64' } = {}) {
  // 1. Encode string to UTF-8 bytes
  const encoder = new TextEncoder();
  const utf8Bytes = encoder.encode(s);

  // 2. Create a ReadableStream from bytes
  const rs = new ReadableStream({
    start(controller) {
      controller.enqueue(utf8Bytes);
      controller.close();
    }
  });

  // 3. Pipe through compression
  const cs = new CompressionStream(encoding);  // e.g. "gzip" or "deflate"
  const compressedStream = rs.pipeThrough(cs);

  // 4. Get the compressed data as ArrayBuffer
  const compressedArrayBuffer = await new Response(compressedStream).arrayBuffer();

  // 5. Encode to desired string form
  if (output === 'base64') {
    return arrayBufferToBase64(compressedArrayBuffer);
  } else if (output === 'hex') {
    return arrayBufferToHex(compressedArrayBuffer);
  } else {
    throw new Error('Unsupported output encoding: ' + output);
  }
}

// Decompress (reverse) from base64 or hex string → original string
async function decompressString(encodedString, { encoding = 'gzip', input = 'base64' } = {}) {
  // 1. Decode the input string to ArrayBuffer
  let compressedBuffer;
  if (input === 'base64') {
    compressedBuffer = base64ToArrayBuffer(encodedString);
  } else if (input === 'hex') {
    compressedBuffer = hexToArrayBuffer(encodedString);
  } else {
    throw new Error('Unsupported input encoding: ' + input);
  }

  // 2. Create a ReadableStream from compressedBuffer
  const rs = new ReadableStream({
    start(controller) {
      controller.enqueue(new Uint8Array(compressedBuffer));
      controller.close();
    }
  });

  // 3. Pipe through DecompressionStream
  const ds = new DecompressionStream(encoding);
  const decompressedStream = rs.pipeThrough(ds);

  // 4. Read decompressed bytes via Response
  const decompressedArrayBuffer = await new Response(decompressedStream).arrayBuffer();

  // 5. Decode UTF-8 to string
  const decoder = new TextDecoder();
  return decoder.decode(decompressedArrayBuffer);
}