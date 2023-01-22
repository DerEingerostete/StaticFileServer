$(document).ready(function() {
    let filepondObject = $('.pond');

    Notiflix.Report.init({
        backgroundColor: '#4b4c53',
        backOverlayColor: 'rgba(0,0,0,0.5)',
        failure: {
            svgColor: '#b1352c',
            buttonBackground: '#b1352c',
            buttonColor: '#d5d2d2',
            titleColor: '#b3b3b3',
            messageColor: '#b9afaf'
        }
    });

    filepondObject.filepond({
        beforeDropFile: (file) => isAllowedFile(file),
        beforeAddFile: (file) => isAllowedFile(file),
        allowMultiple: true,
        maxFiles: 10,
        maxFileSize: '5GB',
        allowRemove: true,
        allowRevert: true,
        maxParallelUploads: 3,
        instantUpload: false,
        chunkUploads: true,
        chunkForce: false,
        chunkSize: 48000000,
        server: {
            process: {
                url: './api/filepond/process',
                method: 'POST'
            },
            revert: {
                url: './api/filepond/revert',
                method: 'POST'
            },
            patch: {
                url: './api/filepond/patch?patch=',
                method: 'PATCH',
                timeout: 600000 // <--- For really big files (over 5GB increase this to allow combination of chunks)
            },
            restore: null,
            load: null,
            fetch: null,
            remove: null
        }
    });

    //document.addEventListener('FilePond:processfile', (event) => {});
    document.addEventListener('FilePond:error', (event) => {
        let statusCode = event.detail.error.code;
        if (statusCode === 409) {
            Notiflix.Report.failure('Upload failed',
                'A file with the same name already exists.',
                'Okay'
            );
        }
    });
});

function isAllowedFile(file) {
    let filename = file.filename;
    let uppercaseName = filename.toUpperCase();

    const illegalChars = ['/', '<', '>', ':', '"', '\\', '|', '?', '*', '\0'];
    for (let char of illegalChars) {
        if (filename.includes(char)) return false;
    }

    const illegalNames = ['CON', 'PRN', 'AUX', 'NUL', 'COM1', 'COM2', 'COM3', 'COM4', 'COM5', 'COM6',
        'COM7', 'COM8', 'COM9', 'LPT1', 'LPT2', 'LPT3', 'LPT4', 'LPT5', 'LPT6', 'LPT7', 'LPT8', 'LPT9']
    for (let name of illegalNames) {
        if (uppercaseName === name) return false;
    }

    if (filename.endsWith(".") || filename.endsWith(" ")) return false;
    for (let i = 0; i < filename.length; i++) {
        let charCode = filename.charCodeAt(i);
        if (charCode >= 0 && charCode <= 31) return false;
    }
    return true;
}