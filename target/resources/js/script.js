window.onload = function () {
    let join = document.getElementById("submit");
    let mySocket, uName, rName, send;

    function setPage() {
        document.body.innerHTML = this.response;
        send = document.getElementById('send');
        let field = document.getElementById('msg');

        send.addEventListener("click", function() {
            let msg = field.value;
            mySocket.send(uName + " " + msg);
        });
    
        field.preventDefault();
        field.addEventListener("keyup", function(event) {
            if(event.keyCode == 13) {
                send.click();
            }
            event.preventDefault();
        });
    }

    join.addEventListener("click", function() {
        uName = document.getElementById('uName').value;
        rName = document.getElementById('rName').value.toLowerCase();
        let xhr = new XMLHttpRequest();
        mySocket = new WebSocket("ws://" + location.host);

        xhr.overrideMimeType("text/plain");
        xhr.addEventListener("load", setPage);
        xhr.open("GET", "body.html");
        xhr.send();

        mySocket.onopen = function() {
            console.log("here");
            mySocket.send("join " + rName);
        }

        mySocket.onmessage = function(event) {
            console.log(event);
            let temp = JSON.parse(event.data);
            console.log(temp)
            let chat = document.getElementById('chatDisplay');
            let container = document.createElement('div');
            container.setAttribute('id', 'msgBox');
            let msg = document.createElement('div');
            if(temp.user == uName) {
                temp.user = "You";
                msg.setAttribute('id', 'you');
            }else {
                msg.setAttribute('id', 'other');
            }
            let text = document.createElement('p');
            text.textContent = "\n" + temp.user + ": " + temp.message + "\n";
            msg.appendChild(text);
            container.appendChild(msg);
            chat.appendChild(container);
            chat.scrollTop = chat.scrollHeight;
            document.getElementById('msg').value = "";
        }
    });

    let alt = document.getElementById('rName');
    alt.addEventListener("keyup", function(event) {
        if(event.keyCode == 13) {
            join.click();
        }
    });
}