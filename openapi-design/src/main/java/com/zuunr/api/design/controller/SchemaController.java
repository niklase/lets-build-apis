package com.zuunr.api.design.controller;

import com.zuunr.json.JsonValue;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class SchemaController {

    @GetMapping(value = "/infomodel-editor")
    public Mono<ResponseEntity<String>> getInfomodelEditor(ServerHttpRequest serverHttpRequest){
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Information model</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            margin: 20px;
                        }
                        #editor {
                            width: 100%;
                            height: 400px;
                            border: 1px solid #ddd;
                            margin-bottom: 10px;
                        }
                        button {
                            padding: 10px 15px;
                            font-size: 16px;
                            cursor: pointer;
                            margin-right: 5px;
                        }
                        .message {
                            margin-top: 20px;
                            font-size: 14px;
                            color: green;
                        }
                        .error {
                            color: red;
                        }
                    </style>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.34.0/min/vs/loader.min.js"></script>
                </head>
                <body>
                                
                    <h1>Information model</h1>
                                
                    <div id="editor"></div>
                    <button id="formatBtn">Format JSON</button>
                    <button id="submitBtn">Save model (and open Swagger tab)</button>
                                
                    <div id="message" class="message"></div>
                                
                    <script>
                        // Load Monaco Editor
                        require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.34.0/min/vs' }});
                        require(['vs/editor/editor.main'], function() {
                            const editor = monaco.editor.create(document.getElementById('editor'), {
                                value: '##INITIAL_MODEL##',
                                language: 'json',
                                theme: 'vs-dark',
                                automaticLayout: true
                            });
                                
                            // Format JSON
                            document.getElementById('formatBtn').addEventListener('click', () => {
                                const unformattedJson = editor.getValue();
                                try {
                                    const jsonObj = JSON.parse(unformattedJson);
                                    const formattedJson = JSON.stringify(jsonObj, null, 4);
                                    editor.setValue(formattedJson);
                                    document.getElementById('message').textContent = 'JSON formatted successfully.';
                                    document.getElementById('message').classList.remove('error');
                                } catch (e) {
                                    document.getElementById('message').textContent = 'Invalid JSON: ' + e.message;
                                    document.getElementById('message').classList.add('error');
                                }
                            });
                                
                            // Submit JSON and redirect
                            document.getElementById('submitBtn').addEventListener('click', () => {
                                const jsonInput = editor.getValue();
                                try {
                                    const jsonObj = JSON.parse(jsonInput); // Validate JSON before sending
                                
                                    fetch('/infomodel', { // Change the URL to your server endpoint
                                        method: 'POST',
                                        headers: {
                                            'Content-Type': 'application/json'
                                        },
                                        body: JSON.stringify(jsonObj)
                                    })
                                    .then(response => {
                                        if (response.ok) {
                                            document.getElementById('message').textContent = 'JSON submitted successfully.';
                                            document.getElementById('message').classList.remove('error');
                                            //window.location.href = '/swagger'; // Redirect to another page
                                        
                                            const newTab = window.open('/swagger', 'swagger-ui');
                                            
                                            // You can optionally add some logic here to interact with the new tab if needed
                                            if (newTab) {
                                                newTab.focus(); // Focus the tab if it was successfully opened
                                            } else {
                                                document.getElementById('message').textContent = 'Failed to open the tab. Please allow popups.';
                                                document.getElementById('message').classList.add('error');
                                            }
                                        
                                        } else {
                                            return response.text().then(text => {
                                                throw new Error(text);
                                            });
                                        }
                                    })
                                    .catch(error => {
                                        document.getElementById('message').textContent = 'Error submitting JSON: ' + error.message;
                                        document.getElementById('message').classList.add('error');
                                    });
                                
                                } catch (e) {
                                    document.getElementById('message').textContent = 'Invalid JSON: ' + e.message;
                                    document.getElementById('message').classList.add('error');
                                }
                            });
                        });
                    </script>
                                
                </body>
                </html>
                                
                """;
                return Mono.just(ResponseEntity.ok(html.replace("##INITIAL_MODEL##", InMemDB.infoModel.asJson())));
    }

    @PostMapping (value = "/infomodel")
    public Mono<ResponseEntity<JsonValue>> postInfomodel(ServerHttpRequest serverHttpRequest) {
        Mono<JsonValue> jsonValueMono = RequestBodyUtils.getBodyAsJsonValue(serverHttpRequest);
        return jsonValueMono.map(jsonValue -> {
            InMemDB.infoModel = jsonValue.getJsonObject();
            return ResponseEntity.ok(jsonValue);
        });
    }
}
