package com.steamzh;

/**
 * 注入到 Steam 页面里的 JavaScript。
 *
 * 它在页面里建立一个全局对象 window.__stzh：
 *   - 自动扫描页面文本，把「像英文、且还没翻译过」的文本登记进队列；
 *   - drain(n)  取出最多 n 条待翻译文本，返回 JSON 字符串；
 *   - apply(json) 把译文写回原来的文本节点；
 *   - setEnabled(bool) 开关；
 *   - setShowSource(bool) 悬停是否显示原文。
 *
 * 注意：这段 JS 里刻意不用反斜杠加 u 的 Unicode 转义（Java 文本块会在编译期处理它），
 * 所以中文判断用 charCodeAt 代替正则。
 */
public class PageScript {

    public static final String INIT = """
            (function(){
              if (window.__stzh) return 'already';

              var SKIP = {SCRIPT:1, STYLE:1, NOSCRIPT:1, CODE:1, PRE:1,
                          TEXTAREA:1, INPUT:1, SELECT:1, SVG:1, CANVAS:1, IFRAME:1};

              var S = { seq: 0, queue: new Map(), done: new WeakSet(), on: true, showSource: true };
              var tip = null;

              function hasCJK(s){
                for (var i=0;i<s.length;i++){
                  var c = s.charCodeAt(i);
                  if (c >= 0x4E00 && c <= 0x9FFF) return true;
                  if (c >= 0x3040 && c <= 0x30FF) return true;
                  if (c >= 0xAC00 && c <= 0xD7AF) return true;
                }
                return false;
              }

              function hasLatin(s){
                return /[A-Za-z]{2,}/.test(s);
              }

              function ok(node){
                if (!node || !node.parentElement) return false;
                var p = node.parentElement;
                if (SKIP[p.tagName]) return false;
                if (p.closest && p.closest('[contenteditable="true"]')) return false;
                var raw = node.nodeValue;
                if (!raw) return false;
                var s = raw.trim();
                if (s.length < 2 || s.length > 1500) return false;
                if (!hasLatin(s)) return false;
                if (hasCJK(s)) return false;
                return true;
              }

              function add(node){
                if (S.done.has(node)) return;
                var text = node.nodeValue.trim();
                var existing = null;
                S.queue.forEach(function(v){ if (!existing && v.text === text) existing = v; });
                if (existing){ existing.nodes.push(node); return; }
                S.seq++;
                S.queue.set(S.seq, { text: text, nodes: [node] });
              }

              function scan(root){
                if (!S.on || !root) return;
                var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
                var n;
                while ((n = w.nextNode())){ if (ok(n)) add(n); }
              }

              function ensureTip(){
                if (tip) return;
                tip = document.createElement('div');
                tip.setAttribute('data-stzh-tip', '1');
                tip.style.cssText = 'position:fixed;z-index:2147483647;display:none;'
                  + 'max-width:520px;padding:7px 11px;border-radius:5px;'
                  + 'background:#1b2838;color:#c7d5e0;border:1px solid #66c0f4;'
                  + 'font:13px/1.5 "Microsoft YaHei",sans-serif;'
                  + 'box-shadow:0 3px 12px rgba(0,0,0,.55);pointer-events:none;'
                  + 'white-space:pre-wrap;';
                document.documentElement.appendChild(tip);

                document.addEventListener('mouseover', function(e){
                  if (!S.showSource || !tip) { return; }
                  var el = e.target && e.target.closest ? e.target.closest('[data-stzh-src]') : null;
                  if (el){
                    tip.textContent = el.getAttribute('data-stzh-src');
                    tip.style.display = 'block';
                  } else {
                    tip.style.display = 'none';
                  }
                }, true);

                document.addEventListener('mousemove', function(e){
                  if (tip && tip.style.display === 'block'){
                    var x = e.clientX + 14, y = e.clientY + 20;
                    if (x + 540 > window.innerWidth) x = e.clientX - 540;
                    tip.style.left = x + 'px';
                    tip.style.top = y + 'px';
                  }
                }, true);
              }

              scan(document.body);

              if (document.body){
                var mo = new MutationObserver(function(list){
                  for (var i=0;i<list.length;i++){
                    var m = list[i];
                    if (m.type === 'characterData'){
                      if (ok(m.target)) add(m.target);
                    } else {
                      for (var j=0;j<m.addedNodes.length;j++){
                        var nd = m.addedNodes[j];
                        if (nd.nodeType === 3){ if (ok(nd)) add(nd); }
                        else if (nd.nodeType === 1){ scan(nd); }
                      }
                    }
                  }
                });
                mo.observe(document.body, {childList:true, subtree:true, characterData:true});
              }

              S.drain = function(max){
                var out = [];
                S.queue.forEach(function(v, k){
                  out.push({ id: k, text: v.text });
                });
                if (max && out.length > max) out = out.slice(0, max);
                return JSON.stringify(out);
              };

              S.apply = function(json){
                var list = JSON.parse(json);
                for (var i=0;i<list.length;i++){
                  var id = list[i].id, tr = list[i].translated;
                  var item = S.queue.get(id);
                  if (!item) continue;
                  if (tr === null || tr === undefined){
                    item.fails = (item.fails || 0) + 1;
                    if (item.fails >= 3) S.queue.delete(id);
                    continue;
                  }
                  ensureTip();
                  for (var j=0;j<item.nodes.length;j++){
                    var node = item.nodes[j];
                    if (node.nodeValue.trim() !== item.text) continue;
                    node.nodeValue = tr;
                    S.done.add(node);
                    var p = node.parentElement;
                    if (p && !p.hasAttribute('data-stzh-src')){
                      p.setAttribute('data-stzh-src', item.text);
                    }
                  }
                  S.queue.delete(id);
                }
              };

              S.pending = function(){ return S.queue.size; };
              S.setEnabled = function(v){ S.on = !!v; };
              S.setShowSource = function(v){
                S.showSource = !!v;
                if (!v && tip) tip.style.display = 'none';
              };
              S.stats = function(){
                return JSON.stringify({ pending: S.queue.size, on: S.on, showSource: S.showSource });
              };

              window.__stzh = S;
              return 'installed';
            })()
            """;
}
