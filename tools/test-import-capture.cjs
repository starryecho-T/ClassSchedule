// Default: real-browser DOM regression tests. --mock: limited Node DOM model tests.
const fs = require('fs');
const path = require('path');
const http = require('http');
const { spawn } = require('child_process');
const assetPath = path.join(__dirname, '../app/src/main/assets/import/capture_schedule.js');
const captureSource = fs.readFileSync(assetPath, 'utf8');
if (process.argv.includes('--mock')) {
    runMockTests();
    return;
}
const browsers = [process.env.CAPTURE_TEST_BROWSER, 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', 'C:/Program Files/Google/Chrome/Application/chrome.exe'].filter(Boolean);
const browser = browsers.find(item => fs.existsSync(item));
if (!browser) throw new Error('Set CAPTURE_TEST_BROWSER to an installed Chromium browser.');

function runMockTests() {
    const vm = require('vm');
    const assert = require('assert');
    // This is intentionally a minimal DOM model, not a substitute for browser tests.
    class Element {
        constructor(name, attrs, children) {
            this.nodeType = 1; this.tagName = name.toUpperCase(); this.attrs = Object.assign({}, attrs); this.childNodes = [];
            for (const child of children || []) this.append(typeof child === 'string' ? { nodeType: 3, nodeValue: child } : child);
        }
        append(child) { child.parentElement = this; child.parentNode = this; this.childNodes.push(child); this.link(); return child; }
        link() { this.childNodes.forEach((child, index) => child.nextSibling = this.childNodes[index + 1] || null); }
        get firstChild() { return this.childNodes[0] || null; }
        get hidden() { return this.attrs.hidden !== undefined; }
        get attributes() { return Object.keys(this.attrs).map(name => ({ name })); }
        getAttribute(name) { return this.attrs[name] === undefined ? null : String(this.attrs[name]); }
        removeAttribute(name) { delete this.attrs[name]; }
        get colSpan() { return Math.max(1, Number(this.attrs.colspan || 1)); }
        get rowSpan() { return this.attrs.rowspan === undefined ? 1 : Number(this.attrs.rowspan); }
        get cells() { return this.childNodes.filter(child => child.nodeType === 1 && /^(TD|TH)$/.test(child.tagName)); }
        get rows() { return this.querySelectorAll('tr').filter(row => { let parent = row.parentElement; while (parent && parent.tagName !== 'TABLE') parent = parent.parentElement; return parent === this; }); }
        get textContent() { return this.childNodes.map(child => child.nodeType === 3 ? child.nodeValue : child.textContent).join(''); }
        set textContent(value) { this.childNodes = []; if (value) this.append({ nodeType: 3, nodeValue: value }); }
        removeChild(child) { this.childNodes.splice(this.childNodes.indexOf(child), 1); this.link(); }
        querySelectorAll(selector) {
            const result = [];
            // 支持 tag.class[attr="value"] 复合选择器（脚本中 td[data-role="item"] 依赖它）。
            const matchesPart = (element, part) => {
                let rest = part;
                const tag = /^([-\w]+)/.exec(rest);
                if (tag) {
                    if (tag[1].toUpperCase() !== element.tagName) return false;
                    rest = rest.slice(tag[0].length);
                }
                while (rest.length > 0) {
                    const attr = /^\[([-\w]+)="([^"]*)"\]/.exec(rest);
                    const cls = /^\.([-\w]+)/.exec(rest);
                    if (attr) {
                        if (element.getAttribute(attr[1]) !== attr[2]) return false;
                        rest = rest.slice(attr[0].length);
                    } else if (cls) {
                        if ((element.getAttribute('class') || '').split(/\s+/).indexOf(cls[1]) < 0) return false;
                        rest = rest.slice(cls[0].length);
                    } else {
                        return false;
                    }
                }
                return true;
            };
            const matches = element => selector.split(',').some(part => part.trim() === '*' || matchesPart(element, part.trim()));
            const walk = parent => { for (const child of parent.childNodes) if (child.nodeType === 1) { if (matches(child)) result.push(child); walk(child); } };
            walk(this); return result;
        }
        querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
        cloneNode(deep) { return new Element(this.tagName, this.attrs, deep ? this.childNodes.map(child => child.nodeType === 3 ? child.nodeValue : child.cloneNode(true)) : []); }
        get outerHTML() { return '<' + this.tagName.toLowerCase() + Object.keys(this.attrs).map(key => ' ' + key + '="' + this.attrs[key] + '"').join('') + '>' + this.childNodes.map(child => child.nodeType === 3 ? child.nodeValue : child.outerHTML).join('') + '</' + this.tagName.toLowerCase() + '>'; }
    }
    const el = (name, attrs = {}, ...children) => new Element(name, attrs, children.flat());
    const cell = (text, attrs = {}) => el('td', attrs, text);
    const row = (...children) => el('tr', {}, ...children);
    const table = (...children) => el('table', {}, el('tbody', {}, ...children));
    function doc(...children) {
        const document = { readyState: 'complete', documentElement: el('html', {}, el('body', {}, ...children)) };
        document.querySelectorAll = selector => document.documentElement.querySelectorAll(selector);
        document.defaultView = { getComputedStyle(element) {
            const values = {};
            for (const item of (element.getAttribute('style') || '').split(';')) { const at = item.indexOf(':'); if (at >= 0) values[item.slice(0, at).trim()] = item.slice(at + 1).trim(); }
            const parentVisibility = element.parentElement ? this.getComputedStyle(element.parentElement).visibility : 'visible';
            return { display: values.display || 'block', opacity: values.opacity || '1', visibility: values.visibility || parentVisibility };
        } };
        const mark = element => { element.ownerDocument = document; for (const child of element.childNodes || []) if (child.nodeType === 1) mark(child); };
        mark(document.documentElement); return document;
    }
    const capture = document => JSON.parse(vm.runInNewContext(captureSource, { document, location: { href: 'https://fixture.invalid/schedule' } }, { timeout: 10000 }));
    let count = 0;
    function test(name, action) { action(); console.log('PASS mock ' + name); count++; }
    test('native table text and br', () => {
        const result = capture(doc(table(row(cell('课程'), cell('地点')), row(el('td', {}, '数学', el('br'), '1-8周'), cell('A101')))));
        assert.deepStrictEqual(result.tables, [{ rows: [['课程', '地点'], ['数学 1-8周', 'A101']] }]); assert.deepStrictEqual(result.warnings, []);
    });
    test('rowspan and colspan', () => {
        const result = capture(doc(table(row(cell('Mon', { rowspan: '2' }), cell('Pair', { colspan: '2' })), row(cell('A'), cell('B')))));
        assert.deepStrictEqual(result.tables[0].rows, [['Mon', 'Pair', 'Pair'], ['Mon', 'A', 'B']]);
    });
    test('rowspan zero', () => { assert.deepStrictEqual(capture(doc(table(row(cell('A', { rowspan: '0' }), cell('B')), row(cell('C'))))).tables[0].rows, [['A', 'B'], ['A', 'C']]); });
    test('hidden text and tables', () => {
        const result = capture(doc(el('div', { style: 'display: none' }, table(row(cell('Hidden')))), table(row(el('td', {}, 'Good', el('span', { hidden: '' }, 'Hidden')), cell('Hidden', { style: 'visibility: hidden' })))));
        assert.deepStrictEqual(result.tables, [{ rows: [['Good', '']] }]);
    });
    test('nested leaf table', () => { assert.deepStrictEqual(capture(doc(table(row(el('td', {}, 'Layout', table(row(cell('Course')))))))).tables, [{ rows: [['Course']] }]); });
    test('ARIA grid', () => {
        const result = capture(doc(el('div', { role: 'grid', 'aria-rowcount': '2', 'aria-colcount': '1' }, el('div', { role: 'row' }, el('span', { role: 'columnheader' }, '课程')), el('div', { role: 'row' }, el('span', { role: 'gridcell' }, '数学')))));
        assert.deepStrictEqual(result.tables, [{ rows: [['课程'], ['数学']] }]); assert.deepStrictEqual(result.warnings, []);
    });
    test('virtual ARIA grid warning', () => { assert(capture(doc(el('div', { role: 'grid', 'aria-rowcount': '10' }, el('div', { role: 'row', 'aria-rowindex': '5' }, el('span', { role: 'cell' }, '数学'))))).warnings.length > 0); });
    test('same-origin frame', () => { const frame = el('iframe'); frame.contentDocument = doc(table(row(cell('Frame')))); const result = capture(doc(frame)); assert.deepStrictEqual(result.tables, [{ rows: [['Frame']] }]); assert(result.html.includes('Same-origin frame snapshot')); });
    test('cross-origin frame warning', () => { const frame = el('iframe'); frame.contentDocument = null; Object.defineProperty(frame, 'contentWindow', { get() { throw new Error('SecurityError'); } }); assert(capture(doc(frame)).warnings.some(item => item.includes('跨域'))); });
    test('JLU grid course cards', () => {
        const card = el('div', { class: 'mtt_arrange_item' },
            el('div', { class: 'mtt_item_kcmc' }, '概率论与数理统计B[07]',
                el('div', { class: 'bh-clearfix jsrs-cont' }, el('div', { class: 'mtt_item_jxbmc bh-pull-left' }, '李亚军'))),
            el('div', { class: 'mtt_item_room' }, '1-2周,4周,星期4,第1节-第2节,前卫-李四光楼-第二阶梯（地学优先）',
                el('a', { class: 'room-preview', href: 'javascript:void(0)' })),
        );
        const item = el('td', { 'data-role': 'item', 'data-week': '4', 'data-begin-unit': '1', 'data-end-unit': '1', rowspan: '2' }, card);
        const result = capture(doc(table(row(el('td', { class: 'mtt_bgcolor_grey' }, '节次/星期'), el('td', { 'data-week': '1' }, '星期一')), row(item))));
        assert.deepStrictEqual(result.cards, [{ week: 4, beginUnit: 1, endUnit: 2, name: '概率论与数理统计B[07]', teacher: '李亚军', time: '1-2周,4周,星期4,第1节-第2节,前卫-李四光楼-第二阶梯（地学优先）' }]);
    });
    test('JLU multiple cards in one cell are all captured', () => {
        const card1 = el('div', { class: 'mtt_arrange_item' }, el('div', { class: 'mtt_item_kcmc' }, '岩石学B', el('div', { class: 'bh-clearfix jsrs-cont' }, el('div', { class: 'mtt_item_jxbmc bh-pull-left' }, '刘娜'))), el('div', { class: 'mtt_item_room' }, '1-2周,4周,星期4,第1节-第2节,阶梯教室'));
        const card2 = el('div', { class: 'mtt_arrange_item' }, el('div', { class: 'mtt_item_kcmc' }, '岩石学B', el('div', { class: 'bh-clearfix jsrs-cont' }, el('div', { class: 'mtt_item_jxbmc bh-pull-left' }, '苟军'))), el('div', { class: 'mtt_item_room' }, '10-12周,星期4,第1节-第2节,实验室132'));
        const item = el('td', { 'data-role': 'item', 'data-week': '4', 'data-begin-unit': '1', 'data-end-unit': '1', rowspan: '2' }, card1, card2);
        const result = capture(doc(table(row(item))));
        assert.strictEqual(result.cards.length, 2);
        assert.strictEqual(result.cards[0].teacher, '刘娜');
        assert.strictEqual(result.cards[1].teacher, '苟军');
        assert.strictEqual(result.cards[0].endUnit, 2);
    });
    test('JLU empty placeholder cells are skipped', () => {
        const result = capture(doc(table(row(el('td', { 'data-role': 'item' }, el('div', { class: 'mtt_arrange_item' }))))));
        assert.deepStrictEqual(result.cards, []);
    });
    test('JLU card missing time stops the capture', () => {
        const result = capture(doc(table(row(el('td', { 'data-role': 'item', 'data-week': '1', 'data-begin-unit': '1', 'data-end-unit': '1' }, el('div', { class: 'mtt_arrange_item' }, el('div', { class: 'mtt_item_kcmc' }, '只有课程名')))))));
        assert.deepStrictEqual(result.cards, []);
        assert(result.warnings.some(item => item.includes('不完整')));
    });
    test('snapshot strips credentials without mutation', () => { const password = el('input', { type: 'password', value: 'PRIVATE' }); const page = doc(table(row(cell('Course'))), password, el('script', {}, 'SECRET')); const result = capture(page); assert(!result.html.includes('PRIVATE') && !result.html.includes('SECRET')); assert(password.getAttribute('value') === 'PRIVATE'); });
    test('table count warning', () => { const result = capture(doc(...Array.from({ length: 41 }, (_, i) => table(row(cell(String(i))))))); assert(result.tables.length === 40 && result.warnings.length > 0); });
    test('global row count warning', () => { const result = capture(doc(table(...Array.from({ length: 600 }, () => row(cell('A')))), table(...Array.from({ length: 600 }, () => row(cell('B')))))); assert(result.tables.reduce((sum, item) => sum + item.rows.length, 0) === 1000 && result.warnings.length > 0); });
    test('column limit warning', () => { const result = capture(doc(table(row(...Array.from({ length: 41 }, () => cell('A')))))); assert(result.tables[0].rows[0].length === 40 && result.warnings.length > 0); });
    test('cell length warning', () => { const result = capture(doc(table(row(cell('A'.repeat(5000)))))); assert(result.tables[0].rows[0][0].length === 4000 && result.warnings.length > 0); });
    test('expanded merged text length warning', () => { const result = capture(doc(table(row(cell('A'.repeat(4000), { rowspan: '10', colspan: '40' })), ...Array.from({ length: 9 }, () => row())))); assert(result.tables[0].rows.flat().join('').length <= 1024 * 1024 && result.warnings.some(item => item.includes('展开后'))); });
    test('UTF-8 snapshot limit warning', () => { const result = capture(doc(table(row(cell('A'))), el('div', {}, '中'.repeat(710000)))); assert(Buffer.byteLength(result.html, 'utf8') <= 2 * 1024 * 1024 && result.warnings.some(item => item.includes('2 MiB'))); });
    console.log(count + '/' + count + ' limited DOM model tests passed; browser DOM verification is separate.');
}

async function browserTests(source) {
    const results = [];
    function assert(value, message) { if (!value) throw new Error(message || 'Assertion failed'); }
    function equal(actual, expected) { assert(JSON.stringify(actual) === JSON.stringify(expected), JSON.stringify(actual) + ' != ' + JSON.stringify(expected)); }
    async function fixture(html) {
        document.body.innerHTML = html;
        const frames = Array.from(document.querySelectorAll('iframe'));
        await Promise.all(frames.map(frame => new Promise(resolve => {
            frame.addEventListener('load', resolve, { once: true });
            setTimeout(resolve, 1500);
        })));
        return JSON.parse(window.eval(source));
    }
    async function test(name, action) {
        try { await action(); results.push({ name, passed: true }); }
        catch (error) { results.push({ name, passed: false, error: String(error.stack || error) }); }
    }
    function table(text) { return '<table><tr><td>' + text + '</td></tr></table>'; }

    await test('native table and whitespace', async () => {
        const result = await fixture('<table><tr><th>课程名称</th><th>详情</th></tr><tr><td>高等数学</td><td>张老师<br>1-8周<div>教A101</div></td></tr></table>');
        equal(result.tables, [{ rows: [['课程名称', '详情'], ['高等数学', '张老师 1-8周 教A101']] }]);
        equal(result.warnings, []);
        assert(result.url === location.href && result.html.includes('高等数学'));
    });
    await test('rowspan and colspan expand consistently', async () => {
        const result = await fixture('<table><tr><th rowspan="2">星期一</th><th colspan="2">课程</th></tr><tr><td>A</td><td>B</td></tr></table>');
        equal(result.tables[0].rows, [['星期一', '课程', '课程'], ['星期一', 'A', 'B']]);
        equal(result.warnings, []);
    });
    await test('rowspan zero ends at row group', async () => {
        const result = await fixture('<table><tbody><tr><td rowspan="0">A</td><td>B</td></tr><tr><td>C</td></tr></tbody><tfoot><tr><td>D</td><td>E</td></tr></tfoot></table>');
        equal(result.tables[0].rows, [['A', 'B'], ['A', 'C'], ['D', 'E']]);
    });
    await test('hidden tables and text excluded', async () => {
        const result = await fixture('<style>.invisible{display:none}</style><div class="invisible">' + table('BAD1') + '</div><div style="visibility:hidden">' + table('BAD2') + '</div><div style="opacity:0">' + table('BAD3') + '</div><div aria-hidden="true">' + table('BAD4') + '</div><table hidden><tr><td>BAD5</td></tr></table><table><tr><td>Good<span style="display:none">BAD6</span></td><td style="visibility:hidden">BAD7</td></tr><tr hidden><td>BAD8</td></tr></table>');
        equal(result.tables, [{ rows: [['Good', '']] }]);
    });
    await test('visible descendant overrides inherited visibility', async () => {
        const result = await fixture('<div style="visibility:hidden"><table style="visibility:visible"><tr><td>Visible</td></tr></table></div>');
        equal(result.tables, [{ rows: [['Visible']] }]);
    });
    await test('nested layout table captured once', async () => {
        const result = await fixture('<table><tr><td>Layout' + table('Course') + '</td></tr></table>');
        equal(result.tables, [{ rows: [['Course']] }]);
    });
    await test('native table with ARIA role is not duplicated', async () => {
        const result = await fixture('<table role="grid"><tr role="row"><td role="gridcell">Course</td></tr></table>');
        equal(result.tables, [{ rows: [['Course']] }]);
    });
    await test('ARIA rows, headers and cells', async () => {
        const result = await fixture('<div role="grid" aria-rowcount="2" aria-colcount="2"><div role="row"><span role="columnheader">课程</span><span role="columnheader">地点</span></div><div role="row"><span role="gridcell">数学</span><span role="cell">A101</span></div></div>');
        equal(result.tables, [{ rows: [['课程', '地点'], ['数学', 'A101']] }]);
        equal(result.warnings, []);
    });
    await test('ARIA spans expand', async () => {
        const result = await fixture('<div role="table"><div role="row"><span role="cell" aria-rowspan="2">Mon</span><span role="cell">A</span></div><div role="row"><span role="cell">B</span></div></div>');
        equal(result.tables[0].rows, [['Mon', 'A'], ['Mon', 'B']]);
    });
    await test('virtualized ARIA grid reports incomplete content', async () => {
        const result = await fixture('<div role="grid" aria-rowcount="100"><div role="row" aria-rowindex="20"><span role="cell" aria-colindex="2">A</span></div></div>');
        assert(result.warnings.length > 0);
    });
    await test('invalid ARIA span reports warning', async () => {
        const result = await fixture('<div role="grid"><div role="row"><span role="cell" aria-colspan="-2">A</span></div></div>');
        assert(result.warnings.length > 0);
    });
    await test('same-origin frames are captured recursively', async () => {
        const result = await fixture('<iframe srcdoc="&lt;table&gt;&lt;tr&gt;&lt;td&gt;Frame course&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;"></iframe>');
        equal(result.tables, [{ rows: [['Frame course']] }]);
        assert(result.html.includes('Same-origin frame snapshot') && result.html.includes('Frame course'));
    });
    await test('sandboxed cross-origin frame reports warning', async () => {
        const result = await fixture(table('Top') + '<iframe sandbox="" srcdoc="&lt;p&gt;Private&lt;/p&gt;"></iframe>');
        equal(result.tables, [{ rows: [['Top']] }]);
        assert(result.warnings.some(item => item.includes('跨域')));
    });
    await test('hidden frame is ignored', async () => {
        const result = await fixture(table('Top') + '<iframe hidden sandbox="" srcdoc="&lt;p&gt;Private&lt;/p&gt;"></iframe>');
        equal(result.warnings, []);
    });
    await test('snapshot strips form values and scripts without mutating page', async () => {
        const result = await fixture(table('Course') + '<input id="password" type="password" value="SECRET-PASSWORD"><textarea id="note">SECRET-NOTE</textarea><script id="script">window.secret = "SECRET-SCRIPT";<\/script><div onclick="secret()">click</div>');
        assert(!result.html.includes('SECRET-PASSWORD') && !result.html.includes('SECRET-NOTE') && !result.html.includes('SECRET-SCRIPT') && !result.html.includes('onclick='));
        assert(document.getElementById('password').value === 'SECRET-PASSWORD');
        assert(document.getElementById('note').value === 'SECRET-NOTE' && document.getElementById('script'));
    });
    await test('table count capped with explicit warning', async () => {
        const result = await fixture(Array.from({ length: 41 }, (_, i) => table(String(i))).join(''));
        assert(result.tables.length === 40 && result.warnings.some(item => item.includes('40 个表格')));
    });
    await test('row count capped with explicit warning', async () => {
        const result = await fixture('<table>' + '<tr><td>A</td></tr>'.repeat(1001) + '</table>');
        assert(result.tables[0].rows.length === 1000 && result.warnings.some(item => item.includes('1000 行')));
    });
    await test('row limit is shared across tables', async () => {
        const result = await fixture('<table>' + '<tr><td>A</td></tr>'.repeat(600) + '</table><table>' + '<tr><td>B</td></tr>'.repeat(600) + '</table>');
        assert(result.tables.reduce((sum, item) => sum + item.rows.length, 0) === 1000 && result.warnings.some(item => item.includes('1000 行')));
    });
    await test('column count capped with explicit warning', async () => {
        const result = await fixture('<table><tr>' + '<td>A</td>'.repeat(41) + '</tr></table>');
        assert(result.tables[0].rows[0].length === 40 && result.warnings.some(item => item.includes('40 列')));
    });
    await test('snapshot UTF-8 size is bounded', async () => {
        const result = await fixture(table('Course') + '<div>' + '中'.repeat(710000) + '</div>');
        assert(new TextEncoder().encode(result.html).length <= 2 * 1024 * 1024);
        assert(result.warnings.some(item => item.includes('2 MiB')));
    });
    await test('single huge cell reports incomplete text', async () => {
        const result = await fixture(table('A'.repeat(5000)));
        assert(result.tables[0].rows[0][0].length === 4000 && result.warnings.length > 0);
    });
    await test('merged cell expansion also has payload limit', async () => {
        const result = await fixture('<table><tr><td rowspan="10" colspan="40">' + 'A'.repeat(4000) + '</td></tr>' + '<tr></tr>'.repeat(9) + '</table>');
        assert(result.tables[0].rows.flat().join('').length <= 1024 * 1024 && result.warnings.some(item => item.includes('展开后')));
    });
    return results;
}

let child;
let completed = false;
let timer;
const profile = fs.mkdtempSync(path.join(require('os').tmpdir(), 'classschedule-capture-'));
const server = http.createServer((request, response) => {
    if (request.url === '/result' && request.method === 'POST') {
        let body = '';
        request.on('data', chunk => body += chunk);
        request.on('end', () => {
            completed = true;
            clearTimeout(timer);
            response.end('ok');
            const results = JSON.parse(body);
            for (const result of results) console.log((result.passed ? 'PASS ' : 'FAIL ') + result.name + (result.error ? '\n' + result.error : ''));
            const passed = results.filter(result => result.passed).length;
            console.log(passed + '/' + results.length + ' browser DOM tests passed');
            process.exitCode = passed === results.length ? 0 : 1;
            server.close();
            if (child) child.kill();
        });
        return;
    }
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    const sourceLiteral = JSON.stringify(captureSource).replace(/</g, '\\u003c');
    response.end('<!DOCTYPE html><html><body><script>(' + browserTests.toString() + ')(' + sourceLiteral + ').then(results => fetch("/result", {method:"POST",body:JSON.stringify(results)})).catch(error => fetch("/result", {method:"POST",body:JSON.stringify([{name:"harness",passed:false,error:String(error.stack)}])}));<\/script></body></html>');
});
server.listen(0, '127.0.0.1', () => {
    const url = 'http://127.0.0.1:' + server.address().port + '/';
    child = spawn(browser, ['--headless=new', '--disable-gpu', '--in-process-gpu', '--disable-extensions', '--no-first-run', '--no-default-browser-check', '--user-data-dir=' + profile, url], { stdio: ['ignore', 'ignore', 'pipe'], windowsHide: true });
    let errors = '';
    child.stderr.on('data', chunk => { errors = (errors + chunk).slice(-3000); });
    child.on('error', error => { clearTimeout(timer); console.error(error); server.close(); process.exitCode = 1; });
    child.on('exit', code => { if (!completed) { clearTimeout(timer); console.error('Browser exited before tests completed: ' + code + '\n' + errors); server.close(); process.exitCode = 1; } });
    timer = setTimeout(() => { console.error('Browser tests timed out\n' + errors); child.kill(); server.close(); process.exitCode = 1; }, 60000);
});
