(() => {
    let notifications = document.createElement('div');
    notifications.className = 'notification'
    document.head.parentElement.appendChild(notifications);
    {
        let link = document.head.appendChild(document.createElement('link'))
        link.rel = 'stylesheet'
        link.href = '/static/css/notification.css'
    }

    function recalcAll() {
        let y = 4;
        let start = notifications.firstElementChild
        while (start) {
            start.style.transform = 'translate(' + start.getAttribute('data-off') + ', ' + y + 'px)';
            y += start.clientHeight;
            y += 4;

            start = start.nextElementSibling
        }
    }

    let randx = "class_" + Math.random().toString().replace('.', '-').replace('-', '_');
    let fakeStyle = document.createElement('style');
    fakeStyle.textContent = "." + randx + "> * { padding: 0 15px }";
    document.head.appendChild(fakeStyle);

    function patchOptions(options) {
        if (options instanceof Element || typeof options != 'object') {
            return patchOptions({
                text: options,
                hideDelay: 3000,
            });
        }
        if (!("hideDelay" in options)) {
            options.hideDelay = 3000
        }
        return options;
    }

    window.MMFNotification = {
        pushMsg: (options) => {
            options = patchOptions(options);
            let text = options.text;

            let newNot = notifications.appendChild(document.createElement('div'));

            if (text instanceof Element) {
                newNot.appendChild(text)
            } else {
                newNot.textContent = text;
            }
            newNot.setAttribute('data-off', '100%');
            recalcAll();

            setTimeout(() => {
                newNot.setAttribute('data-off', '0');
                recalcAll();
            }, 10);

            setTimeout(() => {
                newNot.setAttribute('data-off', '100%');
                recalcAll();
                setTimeout(() => {
                    newNot.remove();
                    recalcAll();
                }, 400)
            }, options.hideDelay)
        },
        openFullScreenAlert: () => {
            let shadow = document.createElement('div');
            shadow.style.position = 'fixed';
            shadow.style.zIndex = '888';
            shadow.style.background = 'rgba(0,0,0,0.5)';
            shadow.style.top = shadow.style.bottom = shadow.style.left = shadow.style.right = '0';
            document.body.appendChild(shadow)

            let content = document.createElement('div');
            {
                let container = document.createElement('div');
                container.className = 'full-center-container';
                shadow.appendChild(container);
                container.appendChild(content);
            }
            content.className = randx;
            content.style.margin = 'auto';
            content.style.background = 'rgb(23,33,43)';
            content.style.padding = '15px 0';
            content.style.borderRadius = '4px';
            content.dispose = () => {
                shadow.remove()
            };


            shadow.addEventListener('click', (event) => {
                if (event.path[0] === shadow) {
                    shadow.remove()
                }
            });
            return content
        },
    };
})();