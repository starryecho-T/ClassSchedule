(function () {
    'use strict';

    // Keep this asset self-contained: it runs in the signed-in WebView page.
    var MAX_TABLES = 40;
    var MAX_CARDS = 300;
    var MAX_ROWS = 1000;
    var MAX_COLUMNS = 40;
    var MAX_DOCUMENTS = 20;
    var MAX_DEPTH = 10;
    var MAX_SNAPSHOT_BYTES = 2 * 1024 * 1024;
    var MAX_CELL_CHARS = 4000;
    var MAX_TEXT_CHARS = 1024 * 1024;
    var tables = [];
    var warnings = [];
    var documents = [];
    var snapshots = [];
    var snapshotBytes = 0;
    var textChars = 0;
    var outputTextChars = 0;
    var candidatesRead = 0;
    var rowsRead = 0;
    var cards = [];

    function warn(message) {
        if (warnings.indexOf(message) < 0) warnings.push(message);
    }

    function normalize(value) {
        return String(value || '').replace(/[\s\u00a0\u3000]+/g, ' ').trim();
    }

    function tag(element) {
        return String(element && element.tagName || '').toLowerCase();
    }

    function role(element) {
        return normalize(element && element.getAttribute && element.getAttribute('role')).toLowerCase();
    }

    function isAriaTable(element) {
        var value = role(element);
        return value === 'table' || value === 'grid' || value === 'treegrid';
    }

    function style(element) {
        try {
            var view = element.ownerDocument && element.ownerDocument.defaultView;
            return view && view.getComputedStyle ? view.getComputedStyle(element) : element.style;
        } catch (ignored) {
            return element.style;
        }
    }

    function visible(element) {
        if (!element || element.nodeType !== 1) return true;
        var ownStyle = style(element);
        if (ownStyle && (ownStyle.visibility === 'hidden' || ownStyle.visibility === 'collapse')) return false;
        var current = element;
        while (current && current.nodeType === 1) {
            if (current.hidden || current.getAttribute('aria-hidden') === 'true') return false;
            var currentStyle = style(current);
            if (currentStyle && (currentStyle.display === 'none' || currentStyle.opacity === '0')) return false;
            current = current.parentElement;
        }
        return true;
    }

    function nearest(element, predicate) {
        var current = element.parentElement;
        while (current) {
            if (predicate(current)) return current;
            current = current.parentElement;
        }
        return null;
    }

    function cellText(element) {
        var pieces = [];
        function walk(node) {
            if (node.nodeType === 3) {
                pieces.push(node.nodeValue || '');
                return;
            }
            if (node.nodeType !== 1 || !visible(node)) return;
            var name = tag(node);
            if (/^(script|style|noscript|template|input|textarea|select|button)$/.test(name)) return;
            if (name === 'br' || name === 'hr') {
                pieces.push(' ');
                return;
            }
            var display = style(node);
            var separates = /^(address|article|aside|blockquote|dd|div|dl|dt|fieldset|figcaption|figure|footer|h[1-6]|header|li|main|nav|ol|p|pre|section|table|td|th|tr|ul)$/.test(name) ||
                display && /^(block|flex|grid|list-item|table-cell|table-row)$/.test(display.display);
            if (separates) pieces.push(' ');
            for (var child = node.firstChild; child; child = child.nextSibling) walk(child);
            if (separates) pieces.push(' ');
        }
        walk(element);
        var value = normalize(pieces.join(''));
        if (value.length > MAX_CELL_CHARS) {
            warn('单元格文字过长，抓取内容不完整，请勿替换课表。');
            value = value.slice(0, MAX_CELL_CHARS);
        }
        if (textChars + value.length > MAX_TEXT_CHARS) {
            warn('表格文字总量超出安全限制，抓取内容不完整，请勿替换课表。');
            value = value.slice(0, Math.max(0, MAX_TEXT_CHARS - textChars));
        }
        textChars += value.length;
        return value;
    }

    function positiveAttribute(element, name, fallback) {
        var raw = element.getAttribute(name);
        if (raw === null || raw === '') return fallback;
        if (!/^\d+$/.test(raw) || Number(raw) < 1) {
            warn('表格包含无法识别的行列索引或合并单元格，请检查导入内容。');
            return fallback;
        }
        return Number(raw);
    }

    function readGrid(sourceRows, ariaRoot) {
        var availableRows = MAX_ROWS - rowsRead;
        if (sourceRows.length > availableRows) warn('表格总计超过 1000 行，抓取内容不完整，请勿替换课表。');
        var rowCount = Math.min(sourceRows.length, availableRows);
        rowsRead += rowCount;
        var grid = [];
        var rowIsVisible = [];
        var width = 0;
        var rowIndex;
        for (rowIndex = 0; rowIndex < rowCount; rowIndex++) grid.push([]);

        for (rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            var row = sourceRows[rowIndex];
            rowIsVisible[rowIndex] = visible(row);
            if (ariaRoot && positiveAttribute(row, 'aria-rowindex', rowIndex + 1) !== rowIndex + 1) {
                warn('表格行索引不连续，页面可能仅加载了部分课程，请勿替换课表。');
            }
            var rawCells = ariaRoot ? row.querySelectorAll('[role="columnheader"], [role="rowheader"], [role="cell"], [role="gridcell"]') : row.cells;
            var cells = [];
            for (var c = 0; c < rawCells.length; c++) {
                var candidate = rawCells[c];
                if (ariaRoot && nearest(candidate, function (item) { return role(item) === 'row'; }) !== row) continue;
                if (ariaRoot && nearest(candidate, isAriaTable) !== ariaRoot) continue;
                cells.push(candidate);
            }
            var column = 0;
            for (var cellIndex = 0; cellIndex < cells.length; cellIndex++) {
                while (column < MAX_COLUMNS && grid[rowIndex][column] !== undefined) column++;
                var cell = cells[cellIndex];
                if (ariaRoot) {
                    var explicitColumn = positiveAttribute(cell, 'aria-colindex', column + 1) - 1;
                    if (explicitColumn !== column) warn('表格列索引不连续或重叠，页面可能仅加载了部分课程，请勿替换课表。');
                    column = explicitColumn;
                }
                var columnSpan = ariaRoot ? positiveAttribute(cell, 'aria-colspan', 1) : cell.colSpan;
                var rowSpan = ariaRoot ? positiveAttribute(cell, 'aria-rowspan', 1) : cell.rowSpan;
                columnSpan = Math.max(1, columnSpan || 1);
                var groupEnd = rowCount;
                if (!ariaRoot) {
                    groupEnd = rowIndex + 1;
                    while (groupEnd < rowCount && sourceRows[groupEnd].parentElement === row.parentElement) groupEnd++;
                }
                if (rowSpan === 0) rowSpan = groupEnd - rowIndex;
                rowSpan = Math.max(1, rowSpan || 1);
                if (ariaRoot && rowIndex + rowSpan > rowCount) {
                    warn('合并单元格引用了尚未加载的行，抓取内容不完整，请勿替换课表。');
                }
                if (column >= MAX_COLUMNS || column + columnSpan > MAX_COLUMNS) {
                    warn('表格超过 40 列，抓取内容不完整，请勿替换课表。');
                }
                if (column >= MAX_COLUMNS) break;
                var value = rowIsVisible[rowIndex] ? cellText(cell) : '';
                var lastRow = Math.min(groupEnd, rowIndex + rowSpan);
                var lastColumn = Math.min(MAX_COLUMNS, column + columnSpan);
                for (var r = rowIndex; r < lastRow; r++) {
                    for (var col = column; col < lastColumn; col++) {
                        if (grid[r][col] !== undefined) {
                            warn('表格合并单元格重叠，无法完整识别课程位置，请勿替换课表。');
                        } else {
                            grid[r][col] = value;
                        }
                    }
                }
                column = lastColumn;
                width = Math.max(width, column);
            }
        }

        var result = [];
        var hasContent = false;
        for (rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            if (!rowIsVisible[rowIndex]) continue;
            var outputRow = [];
            for (var outputColumn = 0; outputColumn < width; outputColumn++) {
                var outputValue = grid[rowIndex][outputColumn] || '';
                // Merged cells repeat their text. Bound the expanded payload as well.
                if (outputTextChars + outputValue.length > MAX_TEXT_CHARS) {
                    warn('展开后的表格文字超出安全限制，抓取内容不完整，请勿替换课表。');
                    outputValue = outputValue.slice(0, Math.max(0, MAX_TEXT_CHARS - outputTextChars));
                }
                outputTextChars += outputValue.length;
                if (outputValue) hasContent = true;
                outputRow.push(outputValue);
            }
            result.push(outputRow);
        }
        if (ariaRoot) {
            var declaredRows = ariaRoot.getAttribute('aria-rowcount');
            var declaredColumns = ariaRoot.getAttribute('aria-colcount');
            if (declaredRows !== null && Number(declaredRows) !== sourceRows.length ||
                declaredColumns !== null && Number(declaredColumns) !== width) {
                warn('表格声明的行列数与已加载内容不一致，可能存在分页或虚拟滚动，请勿替换课表。');
            }
        }
        return hasContent ? result : [];
    }

    function readTables(doc) {
        var nativeTables = doc.querySelectorAll('table');
        var candidates = [];
        for (var i = 0; i < nativeTables.length; i++) {
            var table = nativeTables[i];
            if (!visible(table)) continue;
            var nested = table.querySelectorAll('table');
            var hasVisibleNested = false;
            for (var n = 0; n < nested.length; n++) if (visible(nested[n])) hasVisibleNested = true;
            // Layout tables frequently wrap the actual timetable. Read each leaf once.
            if (!hasVisibleNested) candidates.push({ element: table, aria: false });
        }
        var ariaTables = doc.querySelectorAll('[role="table"], [role="grid"], [role="treegrid"]');
        for (var a = 0; a < ariaTables.length; a++) {
            var ariaTable = ariaTables[a];
            if (!visible(ariaTable) || tag(ariaTable) === 'table' ||
                nearest(ariaTable, function (item) { return tag(item) === 'table'; })) continue;
            var nestedTables = ariaTable.querySelectorAll('table, [role="table"], [role="grid"], [role="treegrid"]');
            var hasVisibleChildTable = false;
            for (var childTable = 0; childTable < nestedTables.length; childTable++) {
                if (visible(nestedTables[childTable])) hasVisibleChildTable = true;
            }
            if (hasVisibleChildTable) continue;
            candidates.push({ element: ariaTable, aria: true });
        }
        for (var candidateIndex = 0; candidateIndex < candidates.length; candidateIndex++) {
            if (candidatesRead >= MAX_TABLES) {
                warn('页面超过 40 个表格，抓取内容不完整，请勿替换课表。');
                return;
            }
            candidatesRead++;
            var item = candidates[candidateIndex];
            var rows = item.aria ? item.element.querySelectorAll('[role="row"]') : item.element.rows;
            var ownRows = [];
            for (var rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                if (item.aria && nearest(rows[rowIndex], isAriaTable) !== item.element) continue;
                ownRows.push(rows[rowIndex]);
            }
            var values = readGrid(ownRows, item.aria ? item.element : null);
            if (values.length) tables.push({ rows: values });
        }
    }

    function directText(element) {
        if (!element) return '';
        var pieces = [];
        for (var child = element.firstChild; child; child = child.nextSibling) {
            if (child.nodeType === 3) pieces.push(child.nodeValue || '');
        }
        return normalize(pieces.join(''));
    }

    // JLU 网格课表：td[data-role="item"] 的属性给出结构位置，卡片内文本给出时间与地点。
    // 只做结构提取，时间语义由应用端解析器校验。
    function readCards(doc) {
        var cells = doc.querySelectorAll('td[data-role="item"]');
        for (var i = 0; i < cells.length; i++) {
            var cell = cells[i];
            if (!visible(cell)) continue;
            var card = cell.querySelector('.mtt_arrange_item') || cell;
            var name = directText(card.querySelector('.mtt_item_kcmc'));
            var time = directText(card.querySelector('.mtt_item_room'));
            if (!name && !time) continue; // 空占位格
            if (!name || !time) {
                warn('课程卡片缺少课程名或上课时间，抓取内容不完整，请勿替换课表。');
                return;
            }
            var teacherNode = card.querySelector('.mtt_item_jxbmc');
            var teacher = teacherNode ? normalize(teacherNode.textContent) : '';
            if (name.length > 200 || teacher.length > 200 || time.length > 400) {
                warn('课程卡片字段过长，页面可能未正常显示课表，请重新抓取。');
                return;
            }
            if (cards.length >= MAX_CARDS) {
                warn('课程卡片数量超过 ' + MAX_CARDS + '，抓取内容不完整，请勿替换课表。');
                return;
            }
            cards.push({
                week: positiveAttribute(cell, 'data-week', 0),
                beginUnit: positiveAttribute(cell, 'data-begin-unit', 0),
                endUnit: positiveAttribute(cell, 'data-end-unit', 0),
                name: name,
                teacher: teacher,
                time: time,
            });
        }
    }

    function boundedUtf8(value, availableBytes) {
        var bytes = 0;
        var index = 0;
        while (index < value.length) {
            var code = value.charCodeAt(index);
            var units = 1;
            var size = code < 128 ? 1 : code < 2048 ? 2 : 3;
            if (code >= 0xd800 && code <= 0xdbff && index + 1 < value.length) {
                var next = value.charCodeAt(index + 1);
                if (next >= 0xdc00 && next <= 0xdfff) { units = 2; size = 4; }
            }
            if (bytes + size > availableBytes) break;
            bytes += size;
            index += units;
        }
        return { text: value.slice(0, index), bytes: bytes, truncated: index < value.length };
    }

    function saveSnapshot(doc, number) {
        if (!doc.documentElement) return;
        var clone = doc.documentElement.cloneNode(true);
        var scripts = clone.querySelectorAll('script');
        for (var i = scripts.length - 1; i >= 0; i--) scripts[i].parentNode.removeChild(scripts[i]);
        var elements = clone.querySelectorAll('*');
        for (var e = -1; e < elements.length; e++) {
            var element = e < 0 ? clone : elements[e];
            if (tag(element) === 'input') element.removeAttribute('value');
            if (tag(element) === 'textarea') element.textContent = '';
            if (tag(element) === 'option') element.removeAttribute('selected');
            // Exported snapshots are for inspection; do not keep executable handlers.
            for (var a = element.attributes.length - 1; a >= 0; a--) {
                var attribute = element.attributes[a].name;
                if (/^on/i.test(attribute) || attribute.toLowerCase() === 'srcdoc') element.removeAttribute(attribute);
            }
        }
        var prefix = number === 0 ? '' : '\n<!-- Same-origin frame snapshot ' + number + ' -->\n';
        var part = boundedUtf8(prefix + clone.outerHTML, MAX_SNAPSHOT_BYTES - snapshotBytes);
        snapshots.push(part.text);
        snapshotBytes += part.bytes;
        if (part.truncated) warn('页面快照超过 2 MiB，快照不完整，请勿直接替换课表。');
    }

    function visit(doc, depth) {
        if (documents.indexOf(doc) >= 0) return;
        if (depth > MAX_DEPTH || documents.length >= MAX_DOCUMENTS) {
            warn('页面嵌套层数或子页面数量超过限制，抓取内容不完整，请勿替换课表。');
            return;
        }
        documents.push(doc);
        try { readTables(doc); } catch (ignored) { warn('部分表格抓取失败，请勿替换课表。'); }
        try { readCards(doc); } catch (ignoredCards) { warn('部分课程卡片抓取失败，请勿替换课表。'); }
        try { saveSnapshot(doc, documents.length - 1); } catch (ignoredSnapshot) { warn('页面快照保存失败，请重新抓取。'); }
        var frames = doc.querySelectorAll('iframe, frame');
        for (var i = 0; i < frames.length; i++) {
            if (!visible(frames[i])) continue;
            try {
                var child = frames[i].contentDocument;
                if (!child && frames[i].contentWindow) child = frames[i].contentWindow.document;
                if (!child || !child.documentElement) {
                    warn('存在尚未加载或无法访问的子页面，请等待加载完成后打开课表页面重新抓取。');
                } else {
                    if (child.readyState === 'loading') warn('课表子页面仍在加载，请等待加载完成后重新抓取。');
                    visit(child, depth + 1);
                }
            } catch (ignoredFrame) {
                warn('存在跨域子页面，浏览器禁止读取；请直接打开该课表页面后重新抓取。');
            }
        }
    }

    try { visit(document, 0); } catch (ignored) { warn('页面抓取失败，请重新打开课表页面。'); }
    return JSON.stringify({ url: location.href, tables: tables, cards: cards, warnings: warnings, html: snapshots.join('') });
})();
