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
    document.getElementById("hashId").innerHTML = idMaps
    document.getElementById('idMapText').value = idMaps
}

function setIdMaps(){
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
    document.getElementById("chooseGeneralOptionAppliedId").style.display = 'block';
    setTimeout(function() {
        document.getElementById("chooseGeneralOptionAppliedId").style.display = 'none';
    }, 5000);

}

function convertArrayToString(array){
    var binary = ""
    while(array.length > 0){
        binary += array.pop()
    }
    return BigInt("0b" + binary).toString(16).toUpperCase();
}

function convertStringToArray(hex){
    return parseInt(hex, 16).toString(2).padStart(hex.length * 4, '0').split('').map(bit => parseInt(bit, 10)).reverse()
}

document.addEventListener("DOMContentLoaded", function() {
    showIdMaps()
    document.getElementById('chooseOptionButtonId').addEventListener('click', function(event) {
        event.preventDefault();
        document.getElementById('categories_dialog').showModal();
        return false;
    })
    document.getElementById('idMapSelect').addEventListener('change', function(event) {
        event.preventDefault();
        if (document.getElementById('idMapSelect').value == "custom"){
            document.getElementById('idMapText').style.display = 'block';
        }else{
            document.getElementById('idMapText').style.display = 'none';
            document.getElementById('idMapText').value=document.getElementById('idMapSelect').value
            setIdMaps()
        }

    })
});