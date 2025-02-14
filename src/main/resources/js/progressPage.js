function updateProgressUI(data) {
    if (data.isCompleted === true) {
        downloadMessage.style.display = "block";
        downloadButton.href = "downloadBundle?taskId=" + data.taskId; // Update this path accordingly
        document.getElementById("progressMessage").textContent = "Support bundle has been generated.";
        downloadButton.click(); // Automatically start the download
    }
}