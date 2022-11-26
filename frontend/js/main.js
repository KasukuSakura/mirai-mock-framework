(() => {
    let domContactList = document.querySelector('.contact-list');
    let domIdleContent = document.querySelector('.maindisplay .idle-content');
    let domChattingMainPanel = document.querySelector('.maindisplay .chatting');
    let domContactName = domChattingMainPanel.querySelector('.topbar div[data-type="contact-name"]');
    let domContactStatus = domChattingMainPanel.querySelector('.topbar div[data-type="contact-status"]');
    let domChatPanel = domChattingMainPanel.querySelector('.chat-panel');
    let domChatterSwitcher = domChattingMainPanel.querySelector('.msg-send>img');
    /**
     * @type {HTMLTextAreaElement}
     */
    let domChatMessageTextarea = domChattingMainPanel.querySelector('.msg-send>textarea');
    let domBtnSend = domChattingMainPanel.querySelector('.msg-send>.btn-send');

    if (!window.forwardEdit) {
        while (domChatPanel.firstChild) {
            domChatPanel.firstChild.remove()
        }

        while (domContactList.firstChild) {
            domContactList.firstChild.remove()
        }

        domIdleContent.removeAttribute('hidden');
        domChattingMainPanel.removeAttribute('hidden');

        domChattingMainPanel.style.display = 'none';
    }

    /**
     * @type { { [contactId: string]: MMF.Contact } }
     */
    let contactList = {};

    /**
     * @type {MMF.Contact[]}
     */
    let friendsList = [];

    /**
     * @type {MMF.Contact[]}
     */
    let groupsList = [];

    /**
     * @type {MMF.BotInfo}
     */
    let botInfoImpl = {
        contactId: '$bot',
        displayName: 'Mock Bot',
        name: 'Mock Bot',
        nativeId: 0,
        type: 'bot',
        chatColor: '#66ccff',
    };

    /**
     * @type {MMF.Contact | undefined}
     */
    let currentActiveContact = undefined;

    Array.prototype.removeByValue = function (item) {
        let index = this.indexOf(item);
        if (index !== -1) {
            this.splice(index, 1);
        }
    }
    Object.defineProperty(Array.prototype, 'removeByValue', {
        enumerable: false,
        value: Array.prototype.removeByValue,
    })

    /**
     * @type {MMF.SingleMessageEditor | null}
     */
    let mainMessageEditor;
    /**
     * @type {MMF.ContactInfo}
     */
    let sendAsContactInfo = botInfoImpl;

    let net_connection = {
        pushWSMessage: (msg) => {
        },
    };

    let sendMessageProcessor = (msg) => {
        console.log(msg)
        MMFNotification.pushMsg(JSON.stringify(msg, null, 2))
        MMFNotification.pushMsg("AS " + JSON.stringify(sendAsContactInfo))

        let msgx = {
            type: 'send-msg',
            subject: currentActiveContact.contactId,
            sender: sendAsContactInfo.nativeId,
            message: msg,
        };
        console.log(msgx)
        net_connection.pushWSMessage(msgx)
        MMFNotification.pushMsg({
            text: JSON.stringify(msgx),
            hideDelay: 10000,
        })


    };

    function openMainMessageEditor() {
        if (mainMessageEditor != null && mainMessageEditor.available) {
            setTimeout(mainMessageEditor.window.focus.bind(mainMessageEditor.window), 100);
            return
        }
        mainMessageEditor = SingleMessageEditor.openEdit(null, sendMessageProcessor);
    }

    function isMainMessageEditorAvailable() {
        return mainMessageEditor != null && mainMessageEditor.available;
    }

    let helpers = {
        /**
         * @typedef T
         * @param arr {T[]}
         * @return {T}
         */
        randomItem: (arr) => {
            let length = arr.length;
            return arr[Math.floor(Math.random() * length)];
        },
        /**
         * @param info {MMF.ContactInfo}
         */
        getAvatarUrl: (info) => {
            if (info.type === 'group') {
                return '/grp-avatar?nid=' + info.nativeId;
            }
            return '/usr-avatar?nid=' + info.nativeId;
        },
        tpadding: (txt) => {
            txt = String(txt)
            if (txt.length === 1) {
                return '0' + txt;
            }
            return txt;
        },
        calcTimeMs: (timestamp) => {
            let date = new Date(timestamp);
            let now = new Date();
            if (now.getFullYear() === date.getFullYear()) {
                if (now.getMonth() === date.getMonth()) {
                    if (now.getDate() === date.getDate()) {
                        return helpers.tpadding(date.getHours()) + ':' + helpers.tpadding(date.getMinutes()) + ':' + helpers.tpadding(date.getSeconds());
                    }
                }
            }
            return date.getMonth() + '/' + date.getDate() + '/' + date.getFullYear();
        },
        /**
         * @param msg {MMF.MiraiMsg.Message | MMF.MiraiMsg.MessageChain}
         */
        renderMessages: (msg) => {
            if (msg instanceof Array) {
                let rsp = document.createElement('div');
                for (let subx of msg) {
                    rsp.append(helpers.renderMessages(subx))
                }
                return rsp;
            }
            switch (msg.type) {
                case "plain": {
                    return document.createTextNode(msg.content)
                }
                case "@": {
                    // TODO
                    return document.createTextNode('@' + msg.target)
                }
                case "image": {
                    let imgx = document.createElement('img');
                    imgx.src = msg.url;
                    return imgx;
                }
                case 'forward': {
                    let view = document.createElement('div');
                    view.textContent = '<<Forward Message>>'

                    view.style.background = '#66ffcc';
                    view.style.color = 'black';
                    view.style.cursor = 'pointer';

                    view.addEventListener('click', () => {
                        SingleMessageEditor.openForwardEdit(msg, () => {
                        })
                    })

                    return view;
                }
            }
        },
        renderMsgSnapshot: (msgs) => {
            if (msgs instanceof Array) {
                let rspx = "";
                for (let subx of msgs) {
                    rspx += helpers.renderMsgSnapshot(subx)
                }
                return rspx;
            }

            switch (msgs.type) {
                case "plain": {
                    return msgs.content
                }
                case "@": {
                    return '@' + msgs.target;
                }
                case 'forward':
                    return "<Forwarded Message>";
            }
            return "";
        },
        scrollIf: (scrollXT, msgp) => {
            //if (scrollXT && msgp.parentElement) {
            // TODO: animation
            msgp.scrollIntoView({behavior: 'smooth'})
            //}
        },
    };
    let lowlevel = {
        member_available_colors: [
            'rgb(157,123,213)',
            'rgb(98,212,173)',
            'rgb(250,163,87)',
            'rgb(116,222,133)',
            'rgb(186,70,118)',
        ],
        helpers: {
            display_for_group: function () {
                return this.name
            },
            display_for_friend: function () {
                return this.nick || this.name;
            },
            display_from_ctinfo: function () {
                return this.contactInfo.displayName
            },
            display_for_member: function () {
                return this.nameCard || this.name;
            },
            bridge_last_spacetimestamp: function () {
                return this.lastSpeakTimestamp
            },
            find_owner: function () {
                return this.members[this.ownerId];
            },
            calc_membercount: function () {
                return Object.keys(this.members).length
            }
        },
        addfriend: (cid, nid, name, nick) => {
            let finfo = {
                type: 'friend',
                name: name,
                nick: nick,
                contactId: cid,
                nativeId: nid,
                lastSpeakTimestamp: 0,
            };
            Object.defineProperty(finfo, 'displayName', {get: lowlevel.helpers.display_for_friend});
            let contact = lowlevel.initContact(finfo);
            contactList[cid] = contact;
            friendsList.push(contact);
            Object.defineProperty(finfo, 'lastSpeakTimestamp', {get: lowlevel.helpers.bridge_last_spacetimestamp.bind(contact)});
            return contact
        },
        createmember: (mid, name, perm, ginfo) => {
            let minfo = {
                type: 'member',
                nameCard: '',
                lastSpeakTimestamp: 0,
                name: name,
                contactId: '',
                nativeId: mid,
                permission: perm,
                chatColor: helpers.randomItem(lowlevel.member_available_colors),
                declaredGroupId: ginfo.contactId,
            };
            Object.defineProperty(minfo, 'displayName', {get: lowlevel.helpers.display_for_member})
            return minfo
        },
        addgroup: (cid, nid, name, ownerNid) => {
            let ginfo = {
                type: 'group',
                name: name,
                contactId: cid,
                nativeId: nid,

                members: {},
                ownerId: ownerNid,
            };
            Object.defineProperty(ginfo, 'owner', {get: lowlevel.helpers.find_owner});
            Object.defineProperty(ginfo, 'displayName', {get: lowlevel.helpers.display_for_group});
            Object.defineProperty(ginfo, 'memberCount', {get: lowlevel.helpers.calc_membercount});

            ginfo.members[ownerNid] = lowlevel.createmember(ownerNid, '', 2);
            let contact = lowlevel.initContact(ginfo);
            contactList[cid] = contact;
            groupsList.push(contact);
            return contact;
        },
        /**
         * @return {MMF.Contact}
         */
        initContact: (cinfo) => {
            let ct = {
                type: cinfo.type,
                contactId: cinfo.contactId,
                nativeId: cinfo.nativeId,
                contactInfo: cinfo,
                subMessages: [],
                lastSpeakTimestamp: Date.now(),
            };
            if (cinfo.type === 'group') {
                ct.groupInfo = cinfo;
            }
            if (cinfo.type === 'friend') {
                ct.friendInfo = cinfo;
            }
            Object.defineProperty(ct, 'displayName', {get: lowlevel.helpers.display_from_ctinfo});
            return ct;
        },
        removeContact: (c) => {
            delete contactList[c.contactId];
            groupsList.removeByValue(c);
            friendsList.removeByValue(c);
            // noinspection EqualityComparisonWithCoercionJS
            if (c == currentActiveContact) {
                currentActiveContact = undefined;
                reloadCurrentContact();
            }
        },
    };


    function rightClickHandler() {
        return (event) => {
            event.preventDefault();
            let domx = document.createElement('div');
            domx.style.zIndex = '99999999';
            // domx.style.background = 'red';
            domx.className = 'right-click-handler';


            let disposeNow = (() => {
                let disposeListener = (event) => {
                    for (let subp of event.path) {
                        if (subp === domx) return;
                    }
                    window.removeEventListener('click', disposeListener);
                    window.removeEventListener('contextmenu', disposeListener);
                    domx.remove();
                };
                setTimeout(() => {
                    window.addEventListener('click', disposeListener);
                    window.addEventListener('contextmenu', disposeListener);
                })
                return () => {
                    window.removeEventListener('click', disposeListener);
                    window.removeEventListener('contextmenu', disposeListener);
                    domx.remove();
                };
            })();

            console.log(arguments)
            for (let item of (arguments[0] instanceof Array ? arguments[0] : arguments)) {
                console.log(item)
                let subitem = domx.appendChild(document.createElement('div'));
                subitem.appendChild(document.createElement('i')).className = item.ico;
                subitem.appendChild(document.createTextNode(item.name))

                subitem.addEventListener('click', item.cb)
                subitem.addEventListener('click', disposeNow)
            }

            document.body.appendChild(domx);
            domx.style.left = event.pageX + 'px';
            domx.style.top = event.pageY + 'px';

            domx.focus({
                preventScroll: true
            });

        };
    }

    /**
     * @param msg {MMF.Messaging.MessageBase}
     */
    function pushMessage(msg) {
        let scrollXT = domChatPanel.scrollTop + domChatPanel.clientHeight >= domChatPanel.scrollHeight - 60


        if (msg.type === 'system') {
            let msgp = document.createElement('div');
            msgp.className = 'systemmsg';
            msgp.textContent = msg.content;
            domChatPanel.appendChild(msgp);
            helpers.scrollIf(scrollXT, msgp)
            return msgp
        }
        if (msg.type === 'normal') {
            /**
             * @type {MMF.Messaging.NormalMessage}
             */
            let msgNormal = msg;

            let msgp = document.createElement('div');
            msgp.className = 'contact-chat';
            if (msgNormal.sender === botInfoImpl) {
                msgp.setAttribute('data-byself', '')
            }

            let imgx = msgp.appendChild(document.createElement('img'));
            imgx.src = helpers.getAvatarUrl(msgNormal.sender);

            let content = msgp.appendChild(document.createElement('div'));
            content.className = 'chat-content';

            console.log(msgNormal)
            console.log(msgNormal.sender)
            let usrname;
            if (window.forwardEdit || (currentActiveContact != null && currentActiveContact.type === 'group')) {
                usrname = content.appendChild(document.createElement('div'));
                usrname.className = 'username';
                usrname.textContent = msgNormal.sender.displayName
                usrname.style.color = msgNormal.sender.chatColor;
            }

            let domMsgContent = content.appendChild(document.createElement('div'));

            domMsgContent.appendChild(helpers.renderMessages(msgNormal.msg));

            let msgTime = msgp.appendChild(document.createElement('span'));
            msgTime.textContent = helpers.calcTimeMs(msg.timestamp);
            msgTime.className = 'time'

            domChatPanel.appendChild(msgp);
            helpers.scrollIf(scrollXT, msgp);


            if (window.forwardEdit) {
                msg.fedit$inode.message = msgNormal.msg;
                msg.fedit$inode.senderName = msgNormal.sender.displayName;
                msg.fedit$inode.timestamp = msgNormal.timestamp
                msg.fedit$inode.sender = msgNormal.sender.nativeId
                forwardEdit.subnodes.push(msg.fedit$inode)
            }

            msgp.addEventListener('contextmenu', rightClickHandler(
                {
                    name: 'Details', ico: 'fa-solid fa-info', cb: () => {
                        console.log('Details', msg);
                        MMFNotification.pushMsg("Please open DevTools to see details");
                        MMFNotification.pushMsg(JSON.stringify(msg, null, 2));
                    }
                },
                {
                    name: 'Reply', ico: 'fa-solid fa-reply', cb: () => {
                        if (window.forwardEdit) {
                            if (!isMainMessageEditorAvailable()) {
                                MMFNotification.pushMsg("Not editing a message. Cancelled.")
                                return
                            }
                        }
                        openMainMessageEditor();
                        if (isMainMessageEditorAvailable()) {
                            mainMessageEditor.postMessage([{
                                type: 'reply',
                                srcIds: msgNormal.ids,
                                srcInternalIds: msgNormal.internalIds,
                                srcTimestamp: msgNormal.timestamp,
                                srcMsgSourceSnapshot: helpers.renderMsgSnapshot(msgNormal.msg),
                            }])
                        }
                    }
                },
                ...(window.forwardEdit ? [
                    {
                        name: "Edit sender name", ico: '', cb: () => {
                            let rsp = prompt("Please enter the new sender name", msg.fedit$inode.senderName);
                            if (rsp != null) {
                                msg.fedit$inode.senderName = rsp;
                                msgNormal.sender.displayName = rsp;
                                usrname.textContent = rsp;
                            }
                        }
                    },
                    {
                        name: "Edit sender id", ico: '', cb: () => {
                            let rsp = prompt("Please enter the new sender id", msg.fedit$inode.sender.toString());
                            if (rsp != null) {
                                msg.fedit$inode.sender = parseInt(rsp);
                                msgNormal.sender.nativeId = parseInt(rsp);
                                imgx.src = helpers.getAvatarUrl(msgNormal.sender);
                                msgNormal.sender.chatColor = forwardEdit.acquireColor(msg.fedit$inode.sender)
                                usrname.style.color = msgNormal.sender.chatColor;
                            }
                        },
                    },
                    {
                        name: "Edit message", ico: '', cb: () => {
                            if (isMainMessageEditorAvailable()) {
                                mainMessageEditor.window.close()
                            }
                            mainMessageEditor = SingleMessageEditor.openEdit(msg.fedit$inode.message, newMsgx => {
                                if (newMsgx == null) return
                                msg.fedit$inode.message = newMsgx
                                msgNormal.message = newMsgx
                                while (domMsgContent.firstElementChild) {
                                    domMsgContent.firstElementChild.remove()
                                }
                                domMsgContent.appendChild(helpers.renderMessages(newMsgx));
                            });
                        },
                    },
                    {
                        name: "Delete", ico: 'fa-solid fa-trash', cb: () => {
                            MMFNotification.pushMsg("DELETE")
                            msgp.remove();
                            forwardEdit.subnodes.removeByValue(msg.fedit$inode)
                        }
                    },
                ] : [])
            ))
            return;
        }
    }


    function reloadCurrentContact() {
        sendAsContactInfo = botInfoImpl;
        reloadSendAs();
        console.log(currentActiveContact);
        {
            let last = domContactList.querySelector('.contact-card[data-active=true]');
            if (currentActiveContact != null && last != null && currentActiveContact.bindDom.card === last) return;
            if (last != null) last.removeAttribute('data-active')
        }
        if (currentActiveContact != null && currentActiveContact.bindDom != null) {
            currentActiveContact.bindDom.card.setAttribute('data-active', 'true')
            domChattingMainPanel.style.display = ''
            domIdleContent.style.display = 'none'

            domContactName.textContent = currentActiveContact.displayName
            if (currentActiveContact.type === 'group') {
                domContactStatus.textContent = currentActiveContact.groupInfo.memberCount + ' members';
            } else {
                domContactStatus.textContent = 'last seen recently';
            }
            while (domChatPanel.firstChild) {
                domChatPanel.firstChild.remove()
            }

            for (let msg of currentActiveContact.subMessages) {
                pushMessage(msg)
            }
        } else {
            domChattingMainPanel.style.display = 'none'
            domIdleContent.style.display = ''
        }
    }

    function reloadContactList() {
        console.log(contactList)
        while (domContactList.firstChild) {
            domContactList.firstChild.remove()
        }
        let tmporders = [];
        for (let contactId in contactList) {
            let contact = contactList[contactId];
            if (contact.bindDom !== undefined) {
                domContactList.appendChild(contact.bindDom.card)
                continue
            }

            let domContact = document.createElement('div');
            domContact.className = 'contact-card';
            domContact.appendChild(document.createElement('img')).src = helpers.getAvatarUrl(contact.contactInfo);

            let texts = domContact.appendChild(document.createElement('div'));
            texts.className = 'contact-card-texts';

            let topline = texts.appendChild(document.createElement('div'));
            topline.className = 'topline';

            let contactName = topline.appendChild(document.createElement('span'));
            contactName.className = 'contact-name';
            contactName.textContent = contact.displayName;

            let contactTime = topline.appendChild(document.createElement('span'));
            contactTime.className = 'contact-time';
            contactTime.textContent = helpers.calcTimeMs(contact.lastSpeakTimestamp);


            texts.appendChild(document.createElement('div')).className = 'linesplit';

            let msgLine = texts.appendChild(document.createElement('div'));
            msgLine.textContent = "MSG";
            contact.bindDom = {
                card: domContact,
                name: contactName,
                time: contactTime,
                msgLine: msgLine,
            };

            domContact.addEventListener('click', () => {
                currentActiveContact = contact;
                reloadCurrentContact();
            });

            tmporders.push(contact)
        }

        tmporders.sort((a, b) => b.lastSpeakTimestamp - a.lastSpeakTimestamp)
        for (let contact of tmporders) {
            domContactList.appendChild(contact.bindDom.card);
        }
    }

    function reloadSendAs() {
        domChatterSwitcher.src = helpers.getAvatarUrl(sendAsContactInfo)
    }

    if (window.forwardEdit) {
        console.log("FORWARD EDIT")

        let colorMapping = {};

        function acquireColor(idx) {
            if (idx in colorMapping) return colorMapping[idx];

            return colorMapping[idx] = helpers.randomItem(lowlevel.member_available_colors);
        }

        forwardEdit.acquireColor = acquireColor;

        window.addEventListener('message', evt => {
            /**
             * @type {MMF.MiraiMsg.Forward}
             */
            let forwardMsg = evt.data;
            if (forwardMsg.nodeList) for (let submsgx of forwardMsg.nodeList) {
                pushMessage({
                    type: 'normal',
                    msg: submsgx.msg,
                    timestamp: submsgx.timestamp,
                    sender: {
                        nativeId: submsgx.senderNativeId,
                        displayName: submsgx.senderName,
                        chatColor: acquireColor(submsgx.senderNativeId),
                    },
                    fedit$inode: {},
                })
            }
        });

        document.querySelector('.editor-btn-add').addEventListener('click', () => {
            pushMessage({
                type: 'normal',
                msg: [{type: 'plain', content: 'Right click to edit this message'}],
                timestamp: Date.now(),
                sender: {
                    nativeId: 0,
                    displayName: "",
                    chatColor: acquireColor(0),
                },
                fedit$inode: {},
            })
        })
        document.querySelector('.editor-btn-complete').addEventListener('click', () => {
            MMFNotification.pushMsg("COMPLETE")
            console.log(forwardEdit.subnodes)
            MMFNotification.pushMsg(JSON.stringify(forwardEdit.subnodes))

            let forwardMsgRe = {
                type: 'forward',
                preview: [],
                title: '转发消息',
                brief: '',
                source: '',
                summary: '共 ' + forwardEdit.subnodes.length + ' 条消息',
                nodeList: [],
            };

            for (let subnode of forwardEdit.subnodes) {
                forwardMsgRe.nodeList.push({
                    senderNativeId: subnode.sender,
                    senderName: subnode.senderName,
                    timestamp: subnode.timestamp,
                    msg: subnode.message,
                })
            }

            window.opener.postMessage(forwardMsgRe)
        })

        return
    }

    window.mainf = {
        helpers: helpers,
        lowlevel: lowlevel,
        botInfoImpl: botInfoImpl,
        contactList: contactList,
        reloadContactList: reloadContactList,
        reloadCurrentContact: reloadCurrentContact,
    };

    domChatterSwitcher.addEventListener('click', () => {
        //MMFNotification.pushMsg("OOOOOOOOOOO!")
        let content = MMFNotification.openFullScreenAlert()
        content.appendChild(document.createElement('h3')).textContent = "Send message as...";
        content.appendChild(document.createElement('span')).textContent = 'Choose the identity you want to use';

        let list = document.createElement('div');
        content.appendChild(list);

        list.className = 'list-select list-select-sendas'

        let itrmap = currentActiveContact.type === 'group' ? currentActiveContact.groupInfo.members : [currentActiveContact.contactInfo];

        console.log(itrmap);

        {
            let lix = list.appendChild(document.createElement('div'));
            if (botInfoImpl === sendAsContactInfo) {
                lix.setAttribute('data-type', 'active');
            }
            lix.appendChild(document.createElement('img')).src = helpers.getAvatarUrl(botInfoImpl);
            let line = lix.appendChild(document.createElement('span'));
            line.textContent = "Mock BOT";

            lix.addEventListener('click', () => {
                sendAsContactInfo = botInfoImpl;
                reloadSendAs();
                content.dispose()
            })
        }
        for (let otherKey in itrmap) {
            let other = itrmap[otherKey];
            if (other.nativeId === botInfoImpl.nativeId) continue


            let lix = list.appendChild(document.createElement('div'));
            if (other === sendAsContactInfo) {
                lix.setAttribute('data-type', 'active');
            }
            lix.appendChild(document.createElement('img')).src = helpers.getAvatarUrl(other);
            let line = lix.appendChild(document.createElement('span'));
            line.textContent = other.displayName;
            let comment = line.appendChild(document.createElement('span'));
            comment.textContent = ' ' + other.nativeId;
            comment.style.color = 'gray';
            comment.style.fontSize = 'small';

            lix.addEventListener('click', () => {
                sendAsContactInfo = other;
                reloadSendAs();
                content.dispose()
            })
        }
    });
    domBtnSend.addEventListener('click', () => {
        MMFNotification.pushMsg("SENDING MSG");
        if (domChatMessageTextarea.value) {
            sendMessageProcessor([{type: 'plain', content: domChatMessageTextarea.value}])
            domChatMessageTextarea.value = '';
        } else {
            MMFNotification.pushMsg("TODO");
            openMainMessageEditor()
        }
    });
    domChatMessageTextarea.addEventListener('keypress', e => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault()
            domBtnSend.click()
        }
    })

    document.querySelector('[data-type="create-new-contact"]').addEventListener('click', () => {
        let mainText = document.createElement('div')

        mainText.textContent = `
        通过 Web 来创建新的联系人对象是不建议的
        更好的做法是通过代码初始化一个新的联系人
        关于如何执行代码, 请参阅
        `.trim();
        mainText.style.whiteSpace = 'normal';

        let httpJump = document.createElement('a');
        httpJump.textContent = "[Document - 执行代码]"
        httpJump.href = "/TODO"; // TODO

        mainText.appendChild(httpJump);


        MMFNotification.pushMsg({
            text: mainText,
            hideDelay: 10000,
        });
    });


    {
        let net_connection_copy = {...net_connection};
        let msgDeque = [];

        let msgQueuePush = net_connection.pushWSMessage = (msg) => {
            msgDeque.push(msg)
        }

        function connectWsDelay() {
            net_connection.sendMessageProcessor = msgQueuePush;

            setTimeout(connectWs, 1000)
        }

        function connectWs() {
            let wsclient = new WebSocket("ws://" + location.host + "/msgc")
            wsclient.onclose = connectWsDelay
            wsclient.onerror = () => {
                wsclient.close()
            }
            wsclient.onopen = () => {
                console.log("WS reconnected...");

                for (let datax of msgDeque) {
                    wsclient.send(JSON.stringify(datax))
                }
                msgDeque.splice(0)
                net_connection.pushWSMessage = msg => {
                    wsclient.send(JSON.stringify(msg))
                }
            }
            wsclient.onmessage = (msge) => {
                let mssgx = JSON.parse(msge.data)
                console.log(mssgx)

                if (mssgx.type === 'not') {
                    MMFNotification.pushMsg(mssgx)
                    return;
                }

                let subj = contactList[mssgx.subject]

                if (subj == null) return
                if (mssgx.type === 'normal') {

                    if (mssgx.sender === "$bot") {
                        mssgx.sender = botInfoImpl
                    } else if (typeof mssgx.sender === 'number') {
                        mssgx.sender = subj.groupInfo.members[mssgx.sender]
                    } else {
                        mssgx.sender = contactList[mssgx.sender]
                    }
                }
                subj.subMessages.push(mssgx)
                subj.lastSpeakTimestamp = Date.now()
                if (currentActiveContact === subj) {
                    pushMessage(mssgx)
                }
                subj.bindDom.time.textContent = helpers.calcTimeMs(subj.lastSpeakTimestamp)
            }
        }

        connectWs()
    }
})();