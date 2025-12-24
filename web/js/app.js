// Основной объект приложения
var AIAssistant = {
    currentSessionId: null,
    eventSource: null,

    // Инициализация
    init: function() {
        this.loadSystemInfo();
        this.connectEventSource();
        this.loadChatHistory();
    },

    // Загрузка системной информации
    loadSystemInfo: function() {
        $.ajax({
            url: '/api/statistics',
            method: 'GET',
            success: function(data) {
                updateSystemInfo(data);
            },
            error: function() {
                $('#status').removeClass('status-connected').addClass('status-error')
                    .html('● Ошибка подключения');
            }
        });

        $.ajax({
            url: '/api/models',
            method: 'GET',
            success: function(data) {
                $('#current-model-name').text(data.current_model);
                $('#model-info').text('Модель: ' + data.current_model);

                // Заполняем выпадающий список моделей
                var modelSelect = $('#model-select');
                modelSelect.combobox({
                    data: data.models.map(function(model) {
                        return {value: model, text: model};
                    }),
                    valueField: 'value',
                    textField: 'text'
                });
            }
        });

        $.ajax({
            url: '/api/prompts',
            method: 'GET',
            success: function(data) {
                $('#current-prompt-mode').text(data.use_chat_mode ? 'ЧАТ' : 'ГЕНЕРАЦИЯ');
                $('#mode-info').text('Режим: ' + (data.use_chat_mode ? 'ЧАТ' : 'ГЕНЕРАЦИЯ'));

                // Заполняем промпты
                $('#chat-prompt-view').text(data.chat_prompt);
                $('#generation-prompt-view').text(data.generation_prompt);

                var currentPrompt = data.use_chat_mode ? data.chat_prompt : data.generation_prompt;
                $('#current-prompt-edit').textbox('setText', currentPrompt);
            }
        });
    },

    // Подключение к EventSource для потоковых данных
    connectEventSource: function() {
        if (this.eventSource) {
            this.eventSource.close();
        }

        this.eventSource = new EventSource('/api/chat_stream');

        this.eventSource.onopen = function() {
            $('#status').removeClass('status-error').addClass('status-connected')
                .html('● Подключено');
        };

        this.eventSource.onerror = function() {
            $('#status').removeClass('status-connected').addClass('status-error')
                .html('● Ошибка подключения');
            setTimeout(function() {
                AIAssistant.connectEventSource();
            }, 5000);
        };

        this.eventSource.onmessage = function(event) {
            try {
                var data = JSON.parse(event.data);
                handleEventSourceMessage(data);
            } catch (e) {
                console.error('Ошибка парсинга события:', e);
            }
        };
    },

    // Загрузка истории чата
    loadChatHistory: function() {
        $.ajax({
            url: '/api/chat_history',
            method: 'GET',
            success: function(data) {
                var historyGrid = $('#history-grid');
                if (!historyGrid.datagrid('options')) {
                    historyGrid.datagrid({
                        data: data.history
                    });
                } else {
                    historyGrid.datagrid('loadData', data.history);
                }
            }
        });
    },

    // Отправка вопроса
    sendQuestion: function() {
        var question = $('#question-input').textbox('getText');
        if (!question.trim()) {
            $.messager.alert('Ошибка', 'Введите вопрос', 'error');
            return;
        }

        this.currentSessionId = 'session_' + Date.now();

        // Показываем вопрос в чате
        addMessageToChat('user', question);

        // Очищаем поле ввода
        $('#question-input').textbox('clear');

        // Отправляем запрос
        $.ajax({
            url: '/api/ask',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                question: question,
                session_id: this.currentSessionId
            }),
            success: function(response) {
                if (response.status === 'processing') {
                    // Показываем индикатор обработки
                    addMessageToChat('assistant', '⌛ Обработка запроса...');
                }
            },
            error: function() {
                addMessageToChat('system', '❌ Ошибка отправки запроса');
            }
        });
    },

    // Добавление знаний
    addKnowledge: function(content, source) {
        if (!content.trim()) {
            $.messager.alert('Ошибка', 'Введите текст для добавления', 'error');
            return false;
        }

        $.ajax({
            url: '/api/add_knowledge',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                content: content,
                source: source || 'web_interface'
            }),
            success: function(response) {
                if (response.status === 'success') {
                    $.messager.alert('Успех', 'Знания успешно добавлены', 'info');
                    AIAssistant.loadSystemInfo(); // Обновляем статистику
                } else {
                    $.messager.alert('Ошибка', response.message || 'Неизвестная ошибка', 'error');
                }
            },
            error: function(xhr) {
                try {
                    var error = JSON.parse(xhr.responseText);
                    $.messager.alert('Ошибка', error.error || 'Ошибка сервера', 'error');
                } catch (e) {
                    $.messager.alert('Ошибка', 'Ошибка соединения', 'error');
                }
            }
        });
    },

    // Смена модели
    switchModel: function(modelName) {
        $.ajax({
            url: '/api/switch_model',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                model_name: modelName
            }),
            success: function(response) {
                if (response.status === 'success') {
                    $.messager.alert('Успех', response.message, 'info');
                    AIAssistant.loadSystemInfo(); // Обновляем информацию
                } else {
                    $.messager.alert('Ошибка', response.message, 'error');
                }
            },
            error: function(xhr) {
                $.messager.alert('Ошибка', 'Ошибка соединения', 'error');
            }
        });
    },

    // Загрузка модели
    pullModel: function(modelName) {
        $('#pull-progress').show();
        $('#pull-status').text('Начинаю загрузку модели: ' + modelName);

        $.ajax({
            url: '/api/pull_model',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                model_name: modelName
            }),
            success: function(response) {
                if (response.status === 'started') {
                    $('#pull-status').text('Загрузка начата. Это может занять несколько минут...');
                }
            },
            error: function() {
                $('#pull-progress').hide();
                $.messager.alert('Ошибка', 'Не удалось начать загрузку', 'error');
            }
        });
    },

    // Обновление промпта
    updatePrompt: function(promptType, newPrompt) {
        $.ajax({
            url: '/api/update_prompt',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                type: promptType,
                prompt: newPrompt
            }),
            success: function(response) {
                if (response.status === 'success') {
                    $.messager.alert('Успех', 'Промпт обновлен', 'info');
                    AIAssistant.loadSystemInfo(); // Обновляем информацию
                } else {
                    $.messager.alert('Ошибка', response.message, 'error');
                }
            },
            error: function() {
                $.messager.alert('Ошибка', 'Ошибка соединения', 'error');
            }
        });
    }
};

// Вспомогательные функции
function updateSystemInfo(stats) {
    $('#doc-count').text('Документов: ' + stats.total_documents);
    $('#memory-info').text('Память: ' + stats.memory_usage_percent);

    var statsHtml = '<div class="easyui-panel" title="Системная статистика" style="margin-bottom:20px;padding:15px">' +
        '<div><strong>Документов в базе знаний:</strong> ' + stats.total_documents + '</div>' +
        '<div><strong>Использование памяти:</strong> ' + stats.memory_usage_percent + '</div>' +
        '<div><strong>Текущая LLM модель:</strong> ' + stats.llm_model + '</div>' +
        '<div><strong>Модель для эмбеддингов:</strong> ' + stats.embedding_model + '</div>' +
        '<div><strong>Режим работы:</strong> ' + (stats.use_chat_mode ? 'ЧАТ' : 'ГЕНЕРАЦИЯ') + '</div>' +
        '<div><strong>Использование кэша:</strong> ' + (stats.use_cache ? 'ВКЛ' : 'ВЫКЛ') + '</div>' +
        '<div><strong>Сообщений в истории чата:</strong> ' + stats.chat_history_size + '</div>' +
        '<div><strong>Озвучка:</strong> ' + (stats.speech_enabled ? 'ВКЛ' : 'ВЫКЛ') + '</div>';

    if (stats.prompt_info) {
        statsHtml += '<div><strong>Длина промпта для чата:</strong> ' + stats.prompt_info.chat_prompt_length + ' символов</div>' +
            '<div><strong>Длина промпта для генерации:</strong> ' + stats.prompt_info.generation_prompt_length + ' символов</div>';
    }

    statsHtml += '</div>';

    $('#stats-content').html(statsHtml);
}

function handleEventSourceMessage(data) {
    switch (data.type) {
        case 'connected':
            console.log('Connected to event source, session:', data.session_id);
            break;

        case 'answer_complete':
            if (data.session_id === AIAssistant.currentSessionId) {
                // Удаляем индикатор обработки и добавляем ответ
                var chat = $('#response-area');
                var messages = chat.children('.message');
                var lastMessage = messages.last();

                if (lastMessage.hasClass('assistant-message') &&
                    lastMessage.text().includes('Обработка запроса')) {
                    lastMessage.remove();
                }

                addMessageToChat('assistant', data.answer);
                AIAssistant.loadChatHistory(); // Обновляем историю
            }
            break;

        case 'model_pull_complete':
            $('#pull-progress').hide();
            if (data.success) {
                $.messager.alert('Успех', 'Модель ' + data.model_name + ' успешно загружена', 'info');
                AIAssistant.loadSystemInfo(); // Обновляем список моделей
            } else {
                $.messager.alert('Ошибка', 'Не удалось загрузить модель ' + data.model_name, 'error');
            }
            break;

        case 'model_pull_error':
            $('#pull-progress').hide();
            $.messager.alert('Ошибка', 'Ошибка загрузки модели: ' + data.error, 'error');
            break;

        case 'error':
            addMessageToChat('system', '❌ Ошибка: ' + data.error);
            break;
    }
}

function addMessageToChat(role, content) {
    var chat = $('#response-area');
    var messageClass = role + '-message';
    var icon = role === 'user' ? '👤' : role === 'assistant' ? '🤖' : '⚙️';

    var messageHtml = '<div class="message ' + messageClass + '">' +
        '<div class="message-header">' + icon + ' ' +
        (role === 'user' ? 'Вы' : role === 'assistant' ? 'Ассистент' : 'Система') +
        ' <span class="message-time">' + new Date().toLocaleTimeString() + '</span></div>' +
        '<div class="message-content">' + content.replace(/\n/g, '<br>') + '</div>' +
        '</div>';

    chat.append(messageHtml);
    chat.scrollTop(chat[0].scrollHeight);
}

// Обработчики UI
function showChat() {
    $('#chat-tabs').tabs('select', 0);
}

function showHistory() {
    $('#chat-tabs').tabs('select', 1);
}

function addKnowledge() {
    $('#chat-tabs').tabs('select', 2);
}

function showStats() {
    $('#chat-tabs').tabs('select', 3);
    AIAssistant.loadSystemInfo();
}

function sendQuestion() {
    AIAssistant.sendQuestion();
}

function clearInput() {
    $('#question-input').textbox('clear');
}

function submitKnowledge() {
    var content = $('#knowledge-content').textbox('getText');
    var source = $('#knowledge-source').textbox('getText') || 'web_interface';

    if (AIAssistant.addKnowledge(content, source)) {
        $('#knowledge-content').textbox('clear');
        $('#knowledge-source').textbox('clear');
    }
}

function clearKnowledgeForm() {
    $('#knowledge-content').textbox('clear');
    $('#knowledge-source').textbox('clear');
}

function manageModels() {
    $('#model-dialog').dialog('open');
}

function listModels() {
    // Переключаемся на вкладку со списком моделей
    $('#model-tabs').tabs('select', 0);
    $('#model-dialog').dialog('open');
}

function pullModel() {
    // Переключаемся на вкладку загрузки моделей
    $('#model-tabs').tabs('select', 1);
    $('#model-dialog').dialog('open');
}

function switchModel() {
    var selectedModel = $('#model-select').combobox('getValue');
    if (selectedModel) {
        AIAssistant.switchModel(selectedModel);
        $('#model-dialog').dialog('close');
    } else {
        $.messager.alert('Ошибка', 'Выберите модель', 'error');
    }
}

function refreshModels() {
    AIAssistant.loadSystemInfo();
    $.messager.alert('Информация', 'Список моделей обновлен', 'info');
}

function startModelPull() {
    var modelName = $('#new-model-name').textbox('getText');
    if (modelName) {
        AIAssistant.pullModel(modelName);
    } else {
        $.messager.alert('Ошибка', 'Введите название модели', 'error');
    }
}

function searchKnowledge() {
    $('#search-dialog').dialog('open');
}

function performSearch() {
    var query = $('#search-query').textbox('getText');
    if (query) {
        $.ajax({
            url: '/api/search_knowledge',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                query: query
            }),
            success: function(response) {
                $('#search-results').html('<div style="color:green">Поиск выполнен. Результаты будут показаны в консоли сервера.</div>');
            },
            error: function() {
                $('#search-results').html('<div style="color:red">Ошибка выполнения поиска</div>');
            }
        });
    }
}

function toggleChatMode() {
    var currentMode = $('#mode-info').text().includes('ЧАТ');
    var newMode = !currentMode;

    $.ajax({
        url: '/api/toggle_chat_mode',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            use_chat_mode: newMode
        }),
        success: function(response) {
            AIAssistant.loadSystemInfo();
            $.messager.alert('Успех', 'Режим изменен на: ' + (newMode ? 'ЧАТ' : 'ГЕНЕРАЦИЯ'), 'info');
        }
    });
}

function toggleCache() {
    var currentCache = $('#stats-content').text().includes('ВКЛ');
    var newCache = !currentCache;

    $.ajax({
        url: '/api/toggle_cache',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            use_cache: newCache
        }),
        success: function(response) {
            AIAssistant.loadSystemInfo();
            $.messager.alert('Успех', 'Кэш: ' + (newCache ? 'ВКЛ' : 'ВЫКЛ'), 'info');
        }
    });
}

function toggleSpeech() {
    var currentSpeech = $('#stats-content').text().includes('ВКЛ');
    var newSpeech = !currentSpeech;

    $.ajax({
        url: '/api/toggle_speech',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            speech_enabled: newSpeech
        }),
        success: function(response) {
            AIAssistant.loadSystemInfo();
            $.messager.alert('Успех', 'Озвучка: ' + (newSpeech ? 'ВКЛ' : 'ВЫКЛ'), 'info');
        }
    });
}

function managePrompts() {
    $('#prompt-dialog').dialog('open');
}

function updateCurrentPrompt() {
    var newPrompt = $('#current-prompt-edit').textbox('getText');
    var currentMode = $('#current-prompt-mode').text();
    var promptType = currentMode === 'ЧАТ' ? 'chat' : 'generation';

    AIAssistant.updatePrompt(promptType, newPrompt);
    $('#prompt-dialog').dialog('close');
}

function resetPrompts() {
    $.messager.confirm('Подтверждение', 'Сбросить промпты к значениям по умолчанию?', function(r) {
        if (r) {
            $.ajax({
                url: '/api/reset_prompts',
                method: 'POST',
                success: function(response) {
                    if (response.status === 'success') {
                        AIAssistant.loadSystemInfo();
                        $.messager.alert('Успех', 'Промпты сброшены', 'info');
                        $('#prompt-dialog').dialog('close');
                    }
                }
            });
        }
    });
}

function clearHistory() {
    $.messager.confirm('Подтверждение', 'Очистить всю историю чата?', function(r) {
        if (r) {
            $.ajax({
                url: '/api/clear_chat_history',
                method: 'POST',
                success: function(response) {
                    if (response.status === 'success') {
                        AIAssistant.loadChatHistory();
                        $('#response-area').empty();
                        addMessageToChat('system', 'История чата очищена');
                        $.messager.alert('Успех', 'История очищена', 'info');
                    }
                }
            });
        }
    });
}

function clearCache() {
    $.messager.confirm('Подтверждение', 'Очистить кэш модели?', function(r) {
        if (r) {
            $.ajax({
                url: '/api/clear_cache',
                method: 'POST',
                success: function(response) {
                    $.messager.alert(response.status === 'success' ? 'Успех' : 'Ошибка',
                        response.message,
                        response.status === 'success' ? 'info' : 'error');
                }
            });
        }
    });
}

// Инициализация при загрузке страницы
$(document).ready(function() {
    AIAssistant.init();
});