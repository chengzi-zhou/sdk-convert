/**
 * SDK 转换台前端逻辑。
 *
 * 页面启动后先拉取 /api/providers 获取渠道、解析示例与转换目标。
 * 两个工作区：
 *   - SDK 转 JSON：调用 /api/parse 把订单串、Scheme、链接或 JSON 转为结构化 JSON；
 *   - 支付链接：调用 /api/convert 把订单串转为 H5 支付链接或 iOS SafePay 链接。
 * 所有服务端返回的内容在插入 DOM 前都会做 HTML 转义。
 */
(function () {
    'use strict';

    var state = {
        providers: [],
        provider: null,
        parseResult: null,
        convertResult: null
    };

    var el = {};
    var toastTimer = null;

    document.addEventListener('DOMContentLoaded', init);

    /**
     * 初始化：缓存元素、绑定事件、加载渠道信息。
     */
    async function init() {
        cacheElements();
        bindEvents();

        try {
            var providers = await api('/api/providers');
            if (!Array.isArray(providers) || providers.length === 0) {
                throw new Error('服务端未注册任何 SDK 渠道');
            }
            state.providers = providers;
            state.provider = providers[0];
            renderChannels();
            applyProvider();
        } catch (error) {
            el.channelList.innerHTML = '<span class="channel-error">渠道加载失败：' +
                escapeHtml(error.message) + '</span>';
            showToast(error.message, 'error');
        }
    }

    /**
     * 集中缓存需要频繁访问的元素。
     */
    function cacheElements() {
        [
            'channelList',
            'parseInput', 'parseBtn', 'clearParse',
            'sampleToggle', 'sampleMenu',
            'resultType', 'copyJson', 'jsonViewer',
            'convertInput', 'convertBtn', 'convertSampleBtn',
            'convertTarget', 'copyConvert', 'convertViewer',
            'toast'
        ].forEach(function (id) {
            el[id] = document.getElementById(id);
        });
    }

    /**
     * 绑定页面事件。两个输入框均支持 Ctrl/⌘ + Enter 快捷执行。
     */
    function bindEvents() {
        document.querySelectorAll('.tab').forEach(function (tab) {
            tab.addEventListener('click', function () {
                switchPanel(tab.dataset.panel);
            });
        });

        el.parseBtn.addEventListener('click', handleParse);
        el.clearParse.addEventListener('click', clearParse);
        el.convertBtn.addEventListener('click', handleConvert);
        el.convertSampleBtn.addEventListener('click', loadConvertSample);

        el.copyJson.addEventListener('click', function () {
            if (!state.parseResult) {
                return;
            }
            copyText(JSON.stringify(state.parseResult.data, null, 2), 'JSON 已复制');
        });

        el.copyConvert.addEventListener('click', function () {
            if (!state.convertResult) {
                return;
            }
            copyText(state.convertResult.content, '转换结果已复制');
        });

        el.sampleToggle.addEventListener('click', function (event) {
            event.stopPropagation();
            toggleMenu();
        });

        document.addEventListener('click', function (event) {
            if (el.sampleMenu.hidden) {
                return;
            }
            if (el.sampleMenu.contains(event.target) || el.sampleToggle.contains(event.target)) {
                return;
            }
            closeMenu();
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeMenu();
            }
        });

        el.parseInput.addEventListener('keydown', function (event) {
            if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                event.preventDefault();
                handleParse();
            }
        });

        el.convertInput.addEventListener('keydown', function (event) {
            if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                event.preventDefault();
                handleConvert();
            }
        });
    }

    /**
     * 切换「SDK 转 JSON / 支付链接」面板。
     */
    function switchPanel(panel) {
        document.querySelectorAll('.tab').forEach(function (tab) {
            var active = tab.dataset.panel === panel;
            tab.classList.toggle('is-active', active);
            tab.setAttribute('aria-selected', String(active));
        });
        document.getElementById('panelParse').classList.toggle('is-active', panel === 'parse');
        document.getElementById('panelConvert').classList.toggle('is-active', panel === 'convert');
        closeMenu();
    }

    /**
     * 渲染渠道切换按钮。
     */
    function renderChannels() {
        el.channelList.innerHTML = '';
        state.providers.forEach(function (provider) {
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'channel' + (provider.id === state.provider.id ? ' is-active' : '');
            button.title = provider.description || provider.name;
            button.innerHTML = '<span class="channel-dot"></span>' + escapeHtml(provider.name);
            button.addEventListener('click', function () {
                switchProvider(provider.id);
            });
            el.channelList.appendChild(button);
        });
    }

    /**
     * 切换渠道时重新填充示例、转换目标并清空当前结果。
     */
    function switchProvider(providerId) {
        if (!state.provider || providerId === state.provider.id) {
            return;
        }
        var provider = state.providers.filter(function (item) {
            return item.id === providerId;
        })[0];
        if (!provider) {
            return;
        }

        state.provider = provider;
        renderChannels();
        applyProvider();
        el.parseInput.value = '';
        el.convertInput.value = '';
        resetParseOutput();
        resetConvertOutput();
        showToast('已切换到 ' + provider.name, 'success');
    }

    /**
     * 用渠道元数据填充动态区域：解析示例菜单、转换目标下拉框。
     */
    function applyProvider() {
        var provider = state.provider;
        if (!provider) {
            return;
        }

        el.sampleMenu.innerHTML = '';
        (provider.parseSamples || []).forEach(function (sample) {
            var item = document.createElement('button');
            item.type = 'button';
            item.className = 'menu-item';
            item.textContent = sample.name;
            item.addEventListener('click', function () {
                el.parseInput.value = sample.content;
                closeMenu();
                el.parseInput.focus();
            });
            el.sampleMenu.appendChild(item);
        });

        el.convertTarget.innerHTML = '';
        (provider.convertTargets || []).forEach(function (target) {
            var option = document.createElement('option');
            option.value = target.id;
            option.textContent = target.name;
            option.title = target.description || '';
            el.convertTarget.appendChild(option);
        });
        if (el.convertTarget.options.length === 0) {
            var option = document.createElement('option');
            option.value = '';
            option.textContent = '暂无转换目标';
            el.convertTarget.appendChild(option);
        }
    }

    /**
     * 根据当前转换目标载入对应示例（convertSamples 与 convertTargets 按下标对应）。
     */
    function loadConvertSample() {
        var provider = state.provider;
        if (!provider) {
            return;
        }
        var targets = provider.convertTargets || [];
        var samples = provider.convertSamples || [];
        var index = 0;
        for (var i = 0; i < targets.length; i++) {
            if (targets[i].id === el.convertTarget.value) {
                index = i;
                break;
            }
        }
        var sample = samples[index];
        el.convertInput.value = sample ? sample.content : '';
        el.convertInput.focus();
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    /**
     * 调用解析接口并渲染 JSON。
     */
    async function handleParse() {
        if (!state.provider) {
            showToast('渠道尚未加载完成', 'warn');
            return;
        }
        var content = el.parseInput.value.trim();
        if (!content) {
            showToast('请先粘贴需要转换的内容', 'warn');
            el.parseInput.focus();
            return;
        }

        setBusy(el.parseBtn, true, '转换中…');
        try {
            var result = await api('/api/parse', {
                method: 'POST',
                body: JSON.stringify({
                    provider: state.provider.id,
                    content: content
                })
            });
            state.parseResult = result;
            renderParseResult(result);
        } catch (error) {
            renderParseError(error.message);
            showToast(error.message, 'error');
        } finally {
            setBusy(el.parseBtn, false, '转换为 JSON');
        }
    }

    /**
     * 渲染解析结果：类型、摘要和高亮 JSON。
     */
    function renderParseResult(result) {
        el.resultType.hidden = false;
        el.resultType.textContent = result.typeName || result.type || '解析成功';
        el.resultType.title = result.type || '';
        el.copyJson.disabled = false;

        var json = JSON.stringify(result.data, null, 2);
        el.jsonViewer.innerHTML =
            '<pre class="json">' + highlightJson(json) + '</pre>';
    }

    /**
     * 渲染解析错误。
     */
    function renderParseError(message) {
        state.parseResult = null;
        el.resultType.hidden = true;
        el.copyJson.disabled = true;
        el.jsonViewer.innerHTML = '<div class="error-card">' +
            '<div class="error-title">转换失败</div><p>' +
            escapeHtml(message) + '</p></div>';
    }

    /**
     * 清空解析输入与结果。
     */
    function clearParse() {
        el.parseInput.value = '';
        resetParseOutput();
        el.parseInput.focus();
    }

    /**
     * 重置解析结果区为占位内容。
     */
    function resetParseOutput() {
        state.parseResult = null;
        el.resultType.hidden = true;
        el.resultType.textContent = '';
        el.copyJson.disabled = true;
        el.jsonViewer.innerHTML = emptyHtml('{ }', '转换后的 JSON 将显示在这里',
            '支持订单串、Scheme、网关链接与 Render 链接');
    }

    // ------------------------------------------------------------------
    // 支付链接转换
    // ------------------------------------------------------------------

    /**
     * 调用转换接口并渲染结果。
     */
    async function handleConvert() {
        if (!state.provider) {
            showToast('渠道尚未加载完成', 'warn');
            return;
        }
        var content = el.convertInput.value.trim();
        if (!content) {
            showToast('请先粘贴订单内容', 'warn');
            el.convertInput.focus();
            return;
        }
        var target = el.convertTarget.value;
        if (!target) {
            showToast('请选择转换目标', 'warn');
            return;
        }

        setBusy(el.convertBtn, true, '转换中…');
        try {
            var result = await api('/api/convert', {
                method: 'POST',
                body: JSON.stringify({
                    provider: state.provider.id,
                    target: target,
                    content: content
                })
            });
            state.convertResult = result;
            renderConvertResult(result);
        } catch (error) {
            renderConvertError(error.message);
            showToast(error.message, 'error');
        } finally {
            setBusy(el.convertBtn, false, '转换为链接');
        }
    }

    /**
     * 渲染转换结果：http(s) 链接直接打开，支付宝 scheme 用按钮触发（浏览器地址栏不能直接拉起）。
     */
    function renderConvertResult(result) {
        el.copyConvert.disabled = false;

        var openHtml = '';
        if (/^https?:\/\//i.test(result.content)) {
            openHtml = '<a class="text-link" href="' + escapeHtml(result.content) +
                '" target="_blank" rel="noopener noreferrer">打开链接 ↗</a>';
        } else if (/^alipay/i.test(result.content)) {
            openHtml = '<button class="link-btn" type="button" data-open-scheme>在手机打开</button>';
        }

        el.convertViewer.innerHTML =
            '<div class="output-meta"><span>' + escapeHtml(result.targetName || '转换结果') + '</span>' +
            openHtml + '</div>' +
            '<pre class="output-text">' + escapeHtml(result.content) + '</pre>';

        var schemeBtn = el.convertViewer.querySelector('[data-open-scheme]');
        if (schemeBtn) {
            schemeBtn.addEventListener('click', function () {
                window.location.href = result.content;
            });
        }
    }

    /**
     * 渲染转换错误。
     */
    function renderConvertError(message) {
        state.convertResult = null;
        el.copyConvert.disabled = true;
        el.convertViewer.innerHTML = '<div class="error-card">' +
            '<div class="error-title">转换失败</div><p>' +
            escapeHtml(message) + '</p></div>';
    }

    /**
     * 重置转换结果区为占位内容。
     */
    function resetConvertOutput() {
        state.convertResult = null;
        el.copyConvert.disabled = true;
        el.convertViewer.innerHTML = emptyHtml('URL', '转换结果将显示在这里',
            '选择转换目标后点击转换');
    }

    // ------------------------------------------------------------------
    // 通用工具
    // ------------------------------------------------------------------

    /**
     * 统一的接口请求封装：校验 HTTP 状态和业务 code，抛出可直接展示的错误信息。
     */
    async function api(path, options) {
        var config = Object.assign({
            headers: { 'Content-Type': 'application/json' }
        }, options || {});

        var response;
        try {
            response = await fetch(path, config);
        } catch (error) {
            throw new Error('无法连接本地服务，请确认应用已启动');
        }

        var payload;
        try {
            payload = await response.json();
        } catch (error) {
            throw new Error('服务返回了非 JSON 响应（HTTP ' + response.status + '）');
        }

        if (!response.ok || !payload || payload.code !== 0) {
            throw new Error((payload && payload.message) || ('请求失败（HTTP ' + response.status + '）'));
        }
        return payload.data;
    }

    /**
     * 切换按钮忙碌状态，避免重复提交。
     */
    function setBusy(button, busy, label) {
        button.disabled = busy;
        var spinner = button.querySelector('.spinner');
        var labelEl = button.querySelector('.btn-label');
        if (spinner) {
            spinner.hidden = !busy;
        }
        if (labelEl) {
            labelEl.textContent = label;
        }
    }

    /**
     * 复制文本，优先使用 Clipboard API，非安全上下文回退到 execCommand。
     */
    async function copyText(text, successMessage) {
        try {
            if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(text);
            } else {
                var textarea = document.createElement('textarea');
                textarea.value = text;
                textarea.style.position = 'fixed';
                textarea.style.opacity = '0';
                document.body.appendChild(textarea);
                textarea.select();
                document.execCommand('copy');
                document.body.removeChild(textarea);
            }
            showToast(successMessage || '已复制', 'success');
        } catch (error) {
            showToast('复制失败，请手动选择复制', 'error');
        }
    }

    /**
     * 轻量提示条，错误和成功共用同一个容器。
     */
    function showToast(message, type) {
        el.toast.textContent = message;
        el.toast.className = 'toast show ' + (type || '');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () {
            el.toast.className = 'toast';
        }, 2600);
    }

    function toggleMenu() {
        var willOpen = el.sampleMenu.hidden;
        el.sampleMenu.hidden = !willOpen;
        el.sampleToggle.classList.toggle('is-open', willOpen);
    }

    function closeMenu() {
        el.sampleMenu.hidden = true;
        el.sampleToggle.classList.remove('is-open');
    }

    function emptyHtml(glyph, title, hint) {
        return '<div class="empty">' +
            '<div class="empty-glyph">' + escapeHtml(glyph) + '</div>' +
            '<p>' + escapeHtml(title) + '</p>' +
            '<span>' + escapeHtml(hint) + '</span>' +
            '</div>';
    }

    /**
     * JSON 语法高亮。
     *
     * 先按 token 拆分再逐段转义，不能先整体转义，否则引号会变成 &quot; 导致
     * 正则无法识别字符串边界。
     */
    function highlightJson(text) {
        var pattern = /("(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*")(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g;
        var result = '';
        var lastIndex = 0;
        var match;

        while ((match = pattern.exec(text)) !== null) {
            result += escapeHtml(text.slice(lastIndex, match.index));

            if (match[1] !== undefined) {
                var isKey = match[2] !== undefined;
                result += '<span class="' + (isKey ? 'j-key' : 'j-str') + '">' +
                    escapeHtml(match[1]) + '</span>';
                if (match[2]) {
                    result += escapeHtml(match[2]);
                }
            } else if (match[3] !== undefined) {
                result += '<span class="' + (match[3] === 'null' ? 'j-null' : 'j-bool') + '">' +
                    escapeHtml(match[3]) + '</span>';
            } else {
                result += '<span class="j-num">' + escapeHtml(match[0]) + '</span>';
            }

            lastIndex = pattern.lastIndex;
        }

        result += escapeHtml(text.slice(lastIndex));
        return result;
    }

    /**
     * 统一的 HTML 转义。
     */
    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
})();
