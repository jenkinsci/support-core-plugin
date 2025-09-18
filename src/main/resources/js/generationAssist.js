function getIdMaps(){
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

function showIdMaps(){
    var idMaps = getIdMaps()
    var mapTextElement = document.getElementById("idMapText");
    if (mapTextElement) { mapTextElement.value = idMaps}
}

function setIdMaps(){
    var chooseGeneralComponentApplyingElement = document.getElementById("chooseGeneralComponentApplyingId")
    var chooseGeneralComponentAppliedElement = document.getElementById("chooseGeneralComponentAppliedId")
    if (chooseGeneralComponentApplyingElement) { chooseGeneralComponentApplyingElement.style.display = 'block' }
    if (chooseGeneralComponentAppliedElement) { chooseGeneralComponentAppliedElement.style.display = 'none'}

    var currentValue = document.getElementById('idMapText').value
    var setWithChecked = convertStringToSet(currentValue)
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


function convertSetToString(setIdMaps){
    return Array.from(setIdMaps).join(',')
}

function convertStringToSet(hex){
    return new Set(hex.split(','))
}

function copyToClipboard() {
    try {
        var mapTextElement = document.getElementById("idMapText");
        if (mapTextElement) {
            navigator.clipboard.writeText(mapTextElement.value)
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