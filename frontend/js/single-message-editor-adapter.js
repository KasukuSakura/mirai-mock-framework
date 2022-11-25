(() => {
    function openMsgWindowCommon(msg, path, callback) {
        let msgFrame = window.open(path, undefined, 'popup=true')
        if (msgFrame) {
            let completed = false;
            let availableForSendMsg = false;
            let dataQueue = [];

            let msgListener = evt => {
                if (evt.source === msgFrame) {
                    completed = true;
                    removeEventListener('message', msgListener);
                    msgFrame.close()
                    callback(evt.data);
                }
            };
            addEventListener('message', msgListener)
            msgFrame.onload = () => {
                availableForSendMsg = true;
                msgFrame.onunload = () => {
                    if (!completed) {
                        completed = true;
                        removeEventListener('message', msgListener);
                        callback(undefined);
                    }
                };

                msgFrame.postMessage(msg);
                for (let subx of dataQueue) {
                    console.log(subx)
                    msgFrame.postMessage(subx)
                }
                dataQueue = undefined;
            }

            let rsp = {
                window: msgFrame,
                postMessage: (data) => {
                    if (availableForSendMsg) {
                        msgFrame.postMessage(data)
                    } else {
                        dataQueue.push(data)
                    }
                },
            }
            Object.defineProperty(rsp, 'available', {
                get: () => {
                    return !completed
                }
            });
            return rsp;
        } else {
            try {
                MMFNotification.pushMsg("Failed to open message editor\nCheck your browser settings and try again later.");
            } catch (ignored) {
            }
            callback(undefined);

            return null
        }
    }

    window.SingleMessageEditor = {
        /**
         * @param forward {MMF.MiraiMsg.Forward}
         * @param callback {(msg: MMF.MiraiMsg.Forward | null) => void}
         * @return {MMF.SingleMessageEditor|null}
         */
        openForwardEdit: (forward, callback) => {
            return openMsgWindowCommon(forward, '/static/forward-edit.html', callback)
        },

        /**
         * @param msg {MMF.MiraiMsg.MessageChain}
         * @param callback {(msg: MMF.MiraiMsg.MessageChain|null ) => void}
         * @return {MMF.SingleMessageEditor|null}
         */
        openEdit: (msg, callback) => {
            return openMsgWindowCommon(msg, '/static/single-message-editor.html', callback)
        },
    };
})();