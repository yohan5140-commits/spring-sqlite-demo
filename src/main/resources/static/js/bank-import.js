/*
 * 통장 입출내역 파일(CSV/TSV, XLSX, 엑셀 형식의 HTML)을 읽어 가계부 내역으로 바꾸는 순수 함수 모음.
 * 브라우저에서는 window.BankImport 로, Node에서는 require/import 로 쓸 수 있다 (src/test/js 참고).
 * 파일은 서버로 보내지 않고 이 브라우저 안에서만 읽는다.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.BankImport = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const MAX_TITLE = 50;
  const MAX_MEMO = 100;
  const MAX_AMOUNT = 100000000000;   // 서버(LedgerService.MAX_AMOUNT)와 같은 상한

  // ---------- 파일 → 표(행 × 열) ----------

  function decodeText(buffer) {
    const bytes = new Uint8Array(buffer);
    try {
      return new TextDecoder('utf-8', { fatal: true }).decode(bytes).replace(/^﻿/, '');
    } catch (e) {
      // 한국 은행의 CSV·HTML은 EUC-KR(CP949)인 경우가 많다
      return new TextDecoder('euc-kr').decode(bytes);
    }
  }

  function detectDelimiter(text) {
    const sample = text.split(/\r?\n/).slice(0, 30).join('\n');
    const count = ch => sample.split(ch).length - 1;
    const candidates = [['\t', count('\t')], [',', count(',')], [';', count(';')]];
    candidates.sort((a, b) => b[1] - a[1]);
    return candidates[0][1] > 0 ? candidates[0][0] : ',';
  }

  function parseDelimited(text) {
    const delimiter = detectDelimiter(text);
    const rows = [];
    let row = [], field = '', quoted = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (quoted) {
        if (c === '"') { if (text[i + 1] === '"') { field += '"'; i++; } else quoted = false; }
        else field += c;
      } else if (c === '"' && field === '') {
        quoted = true;
      } else if (c === delimiter) {
        row.push(field); field = '';
      } else if (c === '\n' || c === '\r') {
        if (c === '\r' && text[i + 1] === '\n') i++;
        row.push(field); field = ''; rows.push(row); row = [];
      } else {
        field += c;
      }
    }
    if (field !== '' || row.length) { row.push(field); rows.push(row); }
    return rows.map(r => r.map(s => s.trim())).filter(r => r.some(c => c !== ''));
  }

  const ENTITIES = { nbsp: ' ', amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };
  function decodeEntities(s) {
    return s.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, e) => {
      if (e[0] === '#') {
        const code = e[1].toLowerCase() === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
        return Number.isFinite(code) ? String.fromCodePoint(code) : m;
      }
      return Object.prototype.hasOwnProperty.call(ENTITIES, e.toLowerCase()) ? ENTITIES[e.toLowerCase()] : m;
    });
  }

  // 엑셀로 저장한 척하는 HTML 표. DOM을 만들지 않고 정규식으로만 읽는다 (스크립트가 실행될 일이 없다)
  function parseHtmlTable(html) {
    const tables = html.match(/<table[\s\S]*?<\/table>/gi) || [];
    let best = [];
    tables.forEach(table => {
      const rows = (table.match(/<tr[\s\S]*?<\/tr>/gi) || []).map(tr =>
        (tr.match(/<t[dh][^>]*>[\s\S]*?<\/t[dh]>/gi) || []).map(td =>
          decodeEntities(td.replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim()));
      if (rows.length > best.length) best = rows;
    });
    return best.filter(r => r.some(c => c !== ''));
  }

  // --- XLSX: zip 안의 XML만 읽는다 (외부 라이브러리 없이) ---

  async function inflateRaw(bytes) {
    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
    return new Uint8Array(await new Response(stream).arrayBuffer());
  }

  function readZipDirectory(bytes) {
    const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let eocd = -1;
    for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
      if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error('엑셀(xlsx) 파일 구조를 읽을 수 없습니다.');
    const count = dv.getUint16(eocd + 10, true);
    let p = dv.getUint32(eocd + 16, true);
    const utf8 = new TextDecoder('utf-8');
    const entries = {};
    for (let n = 0; n < count && dv.getUint32(p, true) === 0x02014b50; n++) {
      const nameLen = dv.getUint16(p + 28, true), extraLen = dv.getUint16(p + 30, true), commentLen = dv.getUint16(p + 32, true);
      entries[utf8.decode(bytes.subarray(p + 46, p + 46 + nameLen))] = {
        encrypted: (dv.getUint16(p + 8, true) & 1) === 1,
        method: dv.getUint16(p + 10, true),
        size: dv.getUint32(p + 20, true),
        offset: dv.getUint32(p + 42, true)
      };
      p += 46 + nameLen + extraLen + commentLen;
    }
    return entries;
  }

  async function readZipEntry(bytes, entry) {
    if (entry.encrypted) throw new Error('암호가 걸린 엑셀 파일입니다. 암호를 해제하고 저장한 뒤 다시 시도해 주세요.');
    const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const start = entry.offset + 30 + dv.getUint16(entry.offset + 26, true) + dv.getUint16(entry.offset + 28, true);
    const data = bytes.subarray(start, start + entry.size);
    if (entry.method === 0) return data;
    if (entry.method === 8) return inflateRaw(data);
    throw new Error('지원하지 않는 압축 방식의 엑셀 파일입니다.');
  }

  const xmlText = s => decodeEntities(s.replace(/<[^>]*>/g, ''));
  const columnIndex = ref => { let n = 0; for (const ch of ref.replace(/[^A-Z]/gi, '').toUpperCase()) n = n * 26 + ch.charCodeAt(0) - 64; return n - 1; };

  function parseSharedStrings(xml) {
    return (xml.match(/<si\b[\s\S]*?<\/si>/g) || []).map(si =>
      (si.replace(/<rPh\b[\s\S]*?<\/rPh>/g, '').match(/<t\b[^>]*>[\s\S]*?<\/t>/g) || []).map(xmlText).join(''));
  }

  function parseSheet(xml, shared) {
    const rows = [];
    (xml.match(/<row\b[^>]*>[\s\S]*?<\/row>/g) || []).forEach(rowXml => {
      const row = [];
      const cells = rowXml.match(/<c\b[^>]*?(?:\/>|>[\s\S]*?<\/c>)/g) || [];
      cells.forEach((cellXml, i) => {
        const attrs = cellXml.match(/<c\b([^>]*?)\/?>/)[1];
        const ref = (attrs.match(/\br="([A-Z]+\d+)"/) || [])[1];
        const type = (attrs.match(/\bt="([^"]+)"/) || [])[1];
        const v = (cellXml.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
        let value = '';
        if (type === 's') value = shared[Number(v)] ?? '';
        else if (type === 'inlineStr') value = xmlText((cellXml.match(/<is>[\s\S]*?<\/is>/) || [''])[0]);
        else if (v !== undefined) value = xmlText(v);
        row[ref ? columnIndex(ref) : i] = value.trim();
      });
      for (let i = 0; i < row.length; i++) if (row[i] === undefined) row[i] = '';
      rows.push(row);
    });
    return rows.filter(r => r.some(c => c !== ''));
  }

  async function parseXlsx(bytes) {
    const entries = readZipDirectory(bytes);
    const sheets = Object.keys(entries).filter(n => /^xl\/worksheets\/sheet\d+\.xml$/.test(n))
      .sort((a, b) => parseInt(a.match(/\d+/)[0], 10) - parseInt(b.match(/\d+/)[0], 10));
    if (!sheets.length) throw new Error('엑셀 파일에서 시트를 찾지 못했습니다.');
    const utf8 = new TextDecoder('utf-8');
    const shared = entries['xl/sharedStrings.xml']
      ? parseSharedStrings(utf8.decode(await readZipEntry(bytes, entries['xl/sharedStrings.xml']))) : [];
    return parseSheet(utf8.decode(await readZipEntry(bytes, entries[sheets[0]])), shared);
  }

  /** 파일 내용을 읽어 행 목록으로 바꾼다. 지원하지 않는 형식이면 이유를 담은 Error를 던진다. */
  async function parseFile(buffer) {
    const bytes = new Uint8Array(buffer);
    if (bytes.length === 0) throw new Error('빈 파일입니다.');
    if (bytes[0] === 0x50 && bytes[1] === 0x4B) return { format: 'xlsx', rows: await parseXlsx(bytes) };
    if (bytes[0] === 0xD0 && bytes[1] === 0xCF && bytes[2] === 0x11 && bytes[3] === 0xE0) {
      throw new Error('암호가 걸렸거나 구형 엑셀(.xls) 파일입니다. 엑셀에서 열어 .xlsx 또는 CSV로 저장한 뒤 다시 시도해 주세요.');
    }
    if (bytes[0] === 0x25 && bytes[1] === 0x50 && bytes[2] === 0x44 && bytes[3] === 0x46) {
      throw new Error('PDF는 읽을 수 없습니다. 인터넷뱅킹에서 엑셀 또는 CSV 형식으로 내려받아 주세요.');
    }
    const text = decodeText(buffer);
    if (/^\s*<(!doctype|html|table|\?xml|meta)/i.test(text)) return { format: 'html', rows: parseHtmlTable(text) };
    return { format: 'csv', rows: parseDelimited(text) };
  }

  // ---------- 열 인식 ----------

  const ROLE_ORDER = ['balance', 'deposit', 'withdrawal', 'date', 'amount', 'kind', 'desc', 'memo'];
  const ROLE_PATTERNS = {
    balance: /잔액|balance/i,
    deposit: /입금|맡기신|받으신|deposit|credit/i,
    withdrawal: /출금|찾으신|보내신|지급|withdraw|debit/i,
    date: /거래\s*일|일\s*시|날\s*짜|일\s*자|거래\s*시|^date/i,
    amount: /^(거래\s*)?금\s*액|^amount/i,
    kind: /입출|거래\s*구분|^구분$/,
    desc: /적요|내\s*용|기재|기록사항|받는\s*분|보낸\s*분|보내는\s*분|상대|의뢰인|거래처|예금주|^desc/i,
    memo: /메모|비고|거래점|취급|점포|remark/i
  };
  const ROLE_LABELS = { date: '날짜', desc: '내용', memo: '메모', deposit: '입금액', withdrawal: '출금액', amount: '금액(부호 포함)', kind: '입출금 구분' };

  function matchColumns(headerRow) {
    const columns = {};
    const taken = new Set();
    ROLE_ORDER.forEach(role => {
      const idx = headerRow.findIndex((h, i) => !taken.has(i) && ROLE_PATTERNS[role].test(h.replace(/\s+/g, ' ').trim()));
      if (idx >= 0) { columns[role] = idx; taken.add(idx); }
    });
    return columns;
  }

  /** 머리글 행을 찾아 열을 자동으로 매핑한다. 못 찾으면 null. */
  function detectColumns(rows) {
    for (let i = 0; i < Math.min(rows.length, 40); i++) {
      const columns = matchColumns(rows[i]);
      if (columns.date !== undefined && (columns.deposit !== undefined || columns.withdrawal !== undefined || columns.amount !== undefined)) {
        return { headerRow: i, columns };
      }
    }
    return null;
  }

  // ---------- 값 해석 ----------

  function isoDate(y, m, d) {
    const t = new Date(Date.UTC(y, m - 1, d));
    if (t.getUTCFullYear() !== y || t.getUTCMonth() !== m - 1 || t.getUTCDate() !== d) return null;
    return String(y).padStart(4, '0') + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0');
  }

  /** 2026.10.27 / 2026-10-27 14:30 / 20261027 / 26.10.27 / 2026년 10월 27일 / 엑셀 날짜 일련번호 → yyyy-MM-dd */
  function parseDate(value) {
    const t = String(value ?? '').trim();
    if (!t) return null;
    let m;
    if (/^\d{5}(\.\d+)?$/.test(t)) {
      const serial = Math.floor(Number(t));
      if (serial > 20000 && serial < 80000) {
        const d = new Date(Date.UTC(1899, 11, 30) + serial * 86400000);
        return isoDate(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
      }
    }
    if ((m = t.match(/^(\d{4})(\d{2})(\d{2})(?!\d)/))) return isoDate(+m[1], +m[2], +m[3]);
    if ((m = t.match(/(\d{4})\s*[.\-\/년]\s*(\d{1,2})\s*[.\-\/월]\s*(\d{1,2})/))) return isoDate(+m[1], +m[2], +m[3]);
    if ((m = t.match(/^(\d{2})[.\-\/](\d{1,2})[.\-\/](\d{1,2})(?!\d)/))) return isoDate(2000 + +m[1], +m[2], +m[3]);
    return null;
  }

  /** "1,234" / "-1,234원" / "(1,234)" → 정수. 숫자가 아니면 null. */
  function parseAmount(value) {
    if (value === null || value === undefined) return null;
    let t = String(value).replace(/[,\s원₩]/g, '');
    if (t === '' || t === '-') return null;
    let negative = false;
    if (/^\(.*\)$/.test(t)) { negative = true; t = t.slice(1, -1); }
    if (t[0] === '-') { negative = !negative; t = t.slice(1); } else if (t[0] === '+') t = t.slice(1);
    if (!/^\d+(\.\d+)?$/.test(t)) return null;
    const n = Math.round(Number(t));
    return negative ? -n : n;
  }

  /**
   * 표의 데이터 행을 내역으로 바꾼다.
   * @param flip 입금/출금 방향을 반대로 해석 (금액 열만 있고 부호가 반대인 은행용)
   * @returns {{entries: Array, skipped: number}} skipped는 날짜나 금액을 읽지 못해 건너뛴 행 수
   */
  function extractEntries(rows, headerRow, columns, flip) {
    const cell = (row, role) => columns[role] === undefined ? '' : (row[columns[role]] ?? '');
    const entries = [];
    let skipped = 0;

    rows.slice(headerRow + 1).forEach((row, i) => {
      const date = parseDate(cell(row, 'date'));
      if (!date) { skipped++; return; }   // 합계·안내 문구 같은 행

      let type = null, amount = null;
      const deposit = parseAmount(cell(row, 'deposit')) || 0;
      const withdrawal = parseAmount(cell(row, 'withdrawal')) || 0;
      if (columns.deposit !== undefined || columns.withdrawal !== undefined) {
        const net = Math.abs(deposit) - Math.abs(withdrawal);
        if (net !== 0) { type = net > 0 ? 'INCOME' : 'EXPENSE'; amount = Math.abs(net); }
      } else {
        const value = parseAmount(cell(row, 'amount'));
        const kind = String(cell(row, 'kind'));
        if (value) {
          amount = Math.abs(value);
          if (/입/.test(kind) && !/출/.test(kind)) type = 'INCOME';
          else if (/출|지급/.test(kind)) type = 'EXPENSE';
          else type = value > 0 ? 'INCOME' : 'EXPENSE';
        }
      }
      if (!type) { skipped++; return; }
      if (flip) type = type === 'INCOME' ? 'EXPENSE' : 'INCOME';

      const desc = String(cell(row, 'desc')).trim();
      const memo = String(cell(row, 'memo')).trim();
      entries.push({
        line: headerRow + 2 + i,
        date, type, amount,
        title: (desc || memo).slice(0, MAX_TITLE),
        memo: desc && memo ? memo.slice(0, MAX_MEMO) : '',
        tooLarge: amount > MAX_AMOUNT
      });
    });
    return { entries, skipped };
  }

  // ---------- 분류 추정 ----------

  const EXPENSE_RULES = [
    ['식비', /스타벅스|이디야|투썸|메가\s*커피|컴포즈|빽다방|카페|커피|맥도날드|버거킹|롯데리아|\bkfc\b|배달의\s*민족|배민|요기요|쿠팡\s*이츠|식당|치킨|피자|김밥|분식|국밥|마트|편의점|\bcu\b|gs\s*25|세븐\s*일레븐|이마트\s*24|베이커리|파리바게뜨|뚜레쥬르|반찬|정육|수산/i],
    ['교통', /지하철|버스|택시|카카오\s*t|티머니|t-?money|코레일|\bktx\b|\bsrt\b|고속|주유|칼텍스|sk\s*에너지|에쓰오일|s-?oil|현대오일|주차|하이패스|도로공사/i],
    ['주거·통신', /관리비|월세|전기|가스|수도|한국전력|한전|통신|skt|sk\s*텔레콤|\bkt\b|lg\s*유플러스|lgu\+|알뜰폰|인터넷/i],
    ['쇼핑', /쿠팡|11번가|g\s*마켓|옥션|네이버\s*페이|네이버\s*쇼핑|무신사|올리브영|다이소|컬리|\bssg\b|지그재그|에이블리|아마존/i],
    ['의료', /병원|의원|약국|치과|한의원|클리닉|의료/],
    ['문화·여가', /영화|\bcgv\b|메가박스|롯데시네마|넷플릭스|왓챠|디즈니|멜론|유튜브|youtube|스포티파이|티빙|웨이브|공연|전시|노래방|게임|스팀|여행|호텔|에어비앤비|항공/i],
    ['교육', /학원|교육|도서|서점|교보문고|영풍문고|예스\s*24|강의|인프런|클래스/]
  ];
  const INCOME_RULES = [
    ['급여', /급여|월급|상여|보너스|salary/i],
    ['부수입', /이자|배당|환급|캐시백|중고|당근|정산/],
    ['용돈', /용돈|생활비|선물/]
  ];

  /** 거래 내용으로 분류를 추정한다. 못 맞추면 '기타'. */
  function guessCategory(type, text) {
    const rules = type === 'INCOME' ? INCOME_RULES : EXPENSE_RULES;
    const hit = rules.find(([, pattern]) => pattern.test(text || ''));
    return hit ? hit[0] : '기타';
  }

  return { parseFile, parseDelimited, parseHtmlTable, detectColumns, matchColumns, extractEntries, parseDate, parseAmount, guessCategory, ROLE_LABELS };
});
