function getIdMaps(){
  var idMaps = []
  var components = document.getElementsByName('selected')
  components.forEach((component, index) => {
    try {
        var position = parseInt(component.id)
        var value = (component.checked)? "1" : "0"
        if(!Number.isNaN(position)){
            if (position >= idMaps.length){
                for(var i = idMaps.length; i < position; i++){
                    idMaps.push("0")
                }
                idMaps.push(value)
            } else {
                idMaps[position] = value
            }
        }
    } catch(error){

    }
  });
  return convertArrayToString(idMaps)
}

function showIdMaps(){
    var idMaps = getIdMaps()
    var hashElement = document.getElementById("hashId");
    var mapTextElement = document.getElementById("idMapText");
    if (hashElement) { hashElement.innerHTML = idMaps }
    if (mapTextElement) { mapTextElement.value = idMaps}
}

function setIdMaps(){
    var chooseGeneralOptionApplyingElement = document.getElementById("chooseGeneralOptionApplyingId")
    var chooseGeneralOptionAppliedElement = document.getElementById("chooseGeneralOptionAppliedId")
    if (chooseGeneralOptionApplyingElement) { chooseGeneralOptionApplyingElement.style.display = 'block' }
    if (chooseGeneralOptionAppliedElement) { chooseGeneralOptionAppliedElement.style.display = 'none'}

    var currentValue = document.getElementById('idMapText').value
    var arrayWithChecked = convertStringToArray(currentValue)
    var components = document.getElementsByName('selected')
    components.forEach((component, index) => {
        try {
            var position = parseInt(component.id)
            if(!Number.isNaN(position) && position < arrayWithChecked.length){
                var desiredValue = arrayWithChecked[position]
                component.checked = (desiredValue=="1")
            }
        } catch(error){

        }
    });
    document.getElementById("hashId").innerHTML = currentValue

    setTimeout(function() {
        if (chooseGeneralOptionApplyingElement) { chooseGeneralOptionApplyingElement.style.display = 'none' }
        if (chooseGeneralOptionAppliedElement) { chooseGeneralOptionAppliedElement.style.display = 'block' }
    }, 1000);

}

function convertArrayToString(array){
    var binary = ""
    while(array.length > 0){
        binary += array.pop()
    }
    if(binary.length > 0){
        return BigInt("0b" + binary).toString(16).toUpperCase();
    } else {
        return ""
    }
}

function convertStringToArray(hex){
    if(hex.length > 0){
        return parseInt(hex, 16).toString(2).padStart(hex.length * 4, '0').split('').map(bit => parseInt(bit, 10)).reverse()
    } else {
        return [];
    }
}

document.addEventListener("DOMContentLoaded", function() {
    var chooseOptionButtonElement = document.getElementById('chooseOptionButtonId')
    var mapTextElement = document.getElementById('idMapText')
    var mapSelectElement = document.getElementById('idMapSelect')
    var mapDivElement = document.getElementById('idMapDiv')
    showIdMaps()

    if (chooseOptionButtonElement) {
        chooseOptionButtonElement.addEventListener('click', function(event) {
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