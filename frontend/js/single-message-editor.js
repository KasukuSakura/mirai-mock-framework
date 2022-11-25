(() => {
    let domLines = document.querySelector('div[data-type="lines"]');
    let domNewElmToolbar = document.querySelector('div[data-type="editor-new-element"] div[data-type="editor-element-types"]');

    console.log(domLines);
    console.log(domNewElmToolbar);

    function newLine() {
        let rsp = document.createElement('div');
        domLines.appendChild(rsp);

        {
            let icoBtn = rsp.appendChild(document.createElement('div'));
            icoBtn.className = 'editor-btn editor-btn-trash'
            icoBtn.appendChild(document.createElement('i')).className = 'fa-solid fa-trash';

            icoBtn.addEventListener('click', rsp.remove.bind(rsp))
        }
        return rsp;
    }

    function createPlainText(_, txt) {
        let line = newLine();
        line.setAttribute('data-type', 'plain');
        let textarea = line.appendChild(document.createElement('textarea'));
        textarea.placeholder = 'Write something here....';
        if (txt) {
            textarea.value = txt
        }
    }

    function createAtTarget(_, target) {
        let line = newLine();
        line.setAttribute('data-type', '@');
        line.appendChild(document.createTextNode('@'));
        let inp = line.appendChild(document.createElement('input'));
        inp.placeholder = '[number] The target you want to @;';
        if (target) inp.value = target;
    }

    function createImage(_, isFlash, data) {
        let line = newLine();
        line.setAttribute('data-type', 'image');

        let label = line.appendChild(document.createElement('label'));
        label.textContent = 'is flash'
        let checkbox = label.appendChild(document.createElement('input'));
        checkbox.type = 'checkbox';
        checkbox.setAttribute('data-type', 'isflash')
        label.setAttribute('data-nogrow', '');

        let rawdata = line.appendChild(document.createElement('input'));
        rawdata.setAttribute('data-type', 'rawdata')
        rawdata.placeholder = 'The internal image data uploaded by ImageUploader.html';
        if (data) rawdata.value = data;
        if (isFlash) checkbox.checked = true;
    }

    function createForward(_, data) {
        let line = newLine();
        line.setAttribute('data-type', 'forward');
        let forward = line.appendChild(document.createElement('div'));
        forward.setAttribute('data-type', 'data');
        forward.textContent = "Forward Message (Click to Edit)";
        forward.className = 'editor-btn';
        forward.style.background = '#66ffcc';
        forward.style.color = 'black';
        data = JSON.stringify({
            nodeList: [
                {
                    senderNativeId: 2736262046,
                    senderName: "寄",
                    timestamp: Date.now(),
                    msg: [{type: 'plain', content: 'hi'}]
                }
            ]
        });
        if (data) {
            forward.setAttribute('data-data', data);
        }

        function readData() {
            let data0 = forward.getAttribute('data-data');
            if (data0 != null && data0.length !== 0) return JSON.parse(data0)
            return null
        }

        forward.addEventListener('click', () => {
            SingleMessageEditor.openForwardEdit(
                readData(), (rspx) => {
                    if (rspx != null) {
                        forward.setAttribute('data-data', JSON.stringify(rspx))
                        console.log(rspx);
                        MMFNotification.pushMsg("Forward message updated\n" + JSON.stringify(rspx, null, 2))
                    }
                }
            )
        });
    }

    function createRawJSON(_, data) {
        let line = newLine();
        line.setAttribute('data-type', 'rawjson');

        line.appendChild(document.createTextNode("##RAW> "))
        let textarea = line.appendChild(document.createElement('textarea'))
        textarea.placeholder = "Raw JSON"
        if (data) {
            textarea.value = data;
        }
    }


    domNewElmToolbar.querySelector('[data-type="plain"]').addEventListener('click', createPlainText);
    domNewElmToolbar.querySelector('[data-type="@"]').addEventListener('click', createAtTarget);
    domNewElmToolbar.querySelector('[data-type="image"]').addEventListener('click', createImage);
    domNewElmToolbar.querySelector('[data-type="forward"]').addEventListener('click', createForward);
    domNewElmToolbar.querySelector('[data-type="rawjson"]').addEventListener('click', createRawJSON);

    function computeMessages() {
        let rsp = [];
        let start = domLines.firstElementChild;
        while (start) {
            let type = start.getAttribute('data-type');
            switch (type) {
                case 'plain' : {
                    rsp.push({type: type, content: start.querySelector('textarea').value})
                    break
                }
                case '@': {
                    rsp.push({type: type, target: parseInt(start.querySelector('input').value)})
                    break
                }
                case 'image': {
                    rsp.push({
                        type: start.querySelector('input[data-type="isflash"]').checked ? 'flashImage' : 'image',
                        internalData: start.querySelector('input[data-type="rawdata"]').value,
                    })
                    break
                }
                case 'forward' : {
                    let datax = start.querySelector('[data-type="data"]').getAttribute('data-data');
                    let datak = {};
                    if (datax) {
                        datak = JSON.parse(datax);
                    }
                    rsp.push({
                        type: 'forward',
                        ...datak,
                    });
                    break
                }
                case 'rawjson': {
                    rsp.push(JSON.parse(start.querySelector('textarea').value))
                    break
                }
            }

            start = start.nextSibling;
        }
        return rsp;
    }

    window.addEventListener('message', evt => {
        console.log(evt)
        if (evt.data == null) return;
        for (let dtx of evt.data) {
            switch (dtx.type) {
                case 'plain':
                    createPlainText(null, dtx.content);
                    break
                case '@':
                    createAtTarget(null, dtx.target)
                    break
                case 'image':
                    createImage(null, false, dtx.internalData);
                    break
                case 'flashImage':
                    createImage(null, true, dtx.internalData);
                    break
                case 'forward':
                    createForward(null, JSON.stringify(dtx))
                    break
                default:
                    createRawJSON(null, JSON.stringify(dtx))
                    break
            }
        }
    });

    function printError(e) {
        let errmsg = document.createElement('pre')
        errmsg.textContent = e.stack
        console.error(e);
        MMFNotification.pushMsg({
            text: errmsg,
            hideDelay: 4000,
        })
    }

    if (window.opener) {
        console.log(window.opener);
        document.querySelector('.editor-btn-complete').addEventListener('click', () => {
            try {
                window.opener.postMessage(computeMessages())
            } catch (e) {
                printError(e)
            }
        })
    } else {
        document.querySelector('.editor-btn-complete').addEventListener('click', () => {
            try {
                let msgx = computeMessages();
                console.log(msgx)
                console.log(JSON.stringify(msgx))
            } catch (e) {
                printError(e)
            }
        })
    }

    window.computeMessages = computeMessages;

})();