package com.zuobiao.browser;

/** 注入用的 JS 片段。全部以字符串常量保存，避免 assets 读取开销。 */
final class Js {

    private Js() {}

    static String night(boolean on) {
        if (!on) {
            return "(function(){var s=document.getElementById('zb-night');if(s)s.remove();"
                    + "document.documentElement.style.background='';"
                    + "if(document.body)document.body.style.filter='';})();";
        }
        // 只对 body 做反色：html 的根背景由 canvas 绘制，不参与 filter，
        // 因此先把 html 压暗、body 设白再反色，避免整页仍是白底。
        return "(function(){try{"
                + "if(document.getElementById('zb-night'))return;"
                + "var s=document.createElement('style');s.id='zb-night';"
                + "s.textContent='html{background:#111!important}"
                + "body{background:#fff!important;filter:invert(1) hue-rotate(180deg)!important}"
                + "img,video,canvas,svg,picture,iframe{filter:invert(1) hue-rotate(180deg)!important}';"
                + "document.documentElement.appendChild(s);"
                + "}catch(e){}})();";
    }

    static String cosmetic(String css) {
        return "(function(){if(document.getElementById('zb-ads'))return;"
                + "var s=document.createElement('style');s.id='zb-ads';"
                + "s.textContent=" + jsString(css) + ";document.documentElement.appendChild(s);})();";
    }

    static String desktopViewport() {
        return "(function(){var m=document.querySelector('meta[name=viewport]');"
                + "if(!m){m=document.createElement('meta');m.name='viewport';"
                + "(document.head||document.documentElement).appendChild(m);}"
                + "m.setAttribute('content','width=1280');})();";
    }

    static String reader(int fontSize) {
        return "(function(){try{"
                + "if(window.__zbReader)return 'already';"
                + "function esc(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}"
                + "function score(el){var ps=el.querySelectorAll('p'),n=0,i;"
                + "for(i=0;i<ps.length;i++){n+=ps[i].innerText.length;}"
                + "return n+el.querySelectorAll('img').length*80;}"
                + "var nodes=document.querySelectorAll('article,main,[role=main],.post,.article,"
                + ".content,#content,.markdown-body,.rich_media_content'),best=null,bs=0,i;"
                + "for(i=0;i<nodes.length;i++){var s=score(nodes[i]);if(s>bs){bs=s;best=nodes[i];}}"
                + "if(!best||bs<300)best=document.body;"
                + "var els=best.querySelectorAll('h1,h2,h3,p,li,blockquote,pre,img,figcaption'),html='';"
                + "for(i=0;i<els.length;i++){var e=els[i],tag=e.tagName.toLowerCase();"
                + "if(tag==='img'){var src=e.currentSrc||e.src;"
                + "if(src&&src.indexOf('data:')!==0)html+='<p><img src=\"'+src+'\"></p>';continue;}"
                + "var t=(e.innerText||'').replace(/\\s+/g,' ').trim();"
                + "if(!t||t.length<2)continue;"
                + "if(tag==='pre'){html+='<pre>'+esc(t)+'</pre>';}"
                + "else if(tag==='li'){html+='<p class=\"li\">· '+esc(t)+'</p>';}"
                + "else{html+='<'+tag+'>'+esc(t)+'</'+tag+'>';}}"
                + "if(html.length<200)return 'empty';"
                + "var title=esc(document.title||'');"
                + "document.body.innerHTML='<div id=\"zb-reader\"><div class=\"t\">'+title+'</div>'+html+'</div>';"
                + "var st=document.createElement('style');st.textContent="
                + jsString("#zb-reader{max-width:720px;margin:0 auto;padding:20px 18px 80px;"
                + "font-size:__SIZE__px;line-height:1.85;color:#1b1b1b;background:#fff;"
                + "font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;"
                + "word-wrap:break-word;}"
                + "#zb-reader .t{font-size:22px;font-weight:700;line-height:1.5;margin:6px 0 18px;}"
                + "#zb-reader p{margin:0 0 1.1em;}#zb-reader .li{margin:0 0 .5em;padding-left:.4em;}"
                + "#zb-reader h1,#zb-reader h2,#zb-reader h3{margin:1.4em 0 .6em;line-height:1.4;}"
                + "#zb-reader img{max-width:100%;height:auto;display:block;margin:1em auto;border-radius:6px;}"
                + "#zb-reader pre{white-space:pre-wrap;background:#f5f5f5;padding:12px;border-radius:8px;"
                + "font-size:.85em;overflow:auto;}"
                + "#zb-reader blockquote{margin:1em 0;padding:.2em 1em;border-left:3px solid #ddd;color:#555;}"
                + "body{margin:0;background:#fff;}")
                .replace("__SIZE__", String.valueOf(fontSize))
                + ";document.head.appendChild(st);"
                + "window.__zbReader=1;window.scrollTo(0,0);return 'ok';"
                + "}catch(e){return 'err:'+e.message;}})();";
    }

    /** 把 Java 字符串安全地变成 JS 字符串字面量（含引号转义）。 */
    static String jsString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** 页面信息：字数、链接数、图片数，用于菜单里的「页面信息」。 */
    static String pageInfo() {
        return "(function(){var ps=document.querySelectorAll('p'),n=0;"
                + "for(var i=0;i<ps.length;i++)n+=ps[i].innerText.length;"
                + "return document.title+'|'+location.href+'|'+n+'|'"
                + "+document.links.length+'|'+document.images.length;})();";
    }
    // ================= 资源嗅探 =================

    /** DOM 层嗅探：video/audio/source、媒体链接、图片、performance 资源，批量回传给 ZBSniff。 */
    static String sniffer(String token) {
        return "(function(){try{"
                + "var TK=" + jsString(token) + ";"
                + "if(window.__zbSniff)return 'already';window.__zbSniff=1;"
                + "var sent={};"
                + "function abs(u){try{return new URL(u,location.href).href}catch(e){return u}}"
                + "function say(list){try{if(list&&list.length)ZBSniff.report(TK,JSON.stringify(list))}catch(e){}}"
                + "function push(out,u,t,extra){u=abs(u);if(!u||u.indexOf('data:')===0)return;"
                + "if(sent[u])return;sent[u]=1;var o={u:u,t:t};if(extra)for(var k in extra)o[k]=extra[k];out.push(o)}"
                + "function scan(){"
                + "var out=[];"
                + "var vs=document.querySelectorAll('video,audio');"
                + "for(var i=0;i<vs.length;i++){var v=vs[i];var s=v.currentSrc||v.src||'';"
                + "if(!s){var so=v.querySelector('source');if(so)s=so.src||''}"
                + "if(!s)continue;var e={d:isFinite(v.duration)?Math.round(v.duration*1000):-1};"
                + "if(v.poster)e.p=abs(v.poster);"
                + "push(out,s,v.tagName==='VIDEO'?'video':'audio',e);"
                + "if(v.tagName==='VIDEO'&&isFinite(v.duration)&&v.duration>0){"
                + "try{ZBSniff.video(TK,JSON.stringify({d:Math.round(v.duration*1000),w:v.videoWidth,h:v.videoHeight,p:v.poster?abs(v.poster):''}))}catch(e){}}}"
                + "var as=document.querySelectorAll('a[href]');"
                + "for(var j=0;j<as.length;j++){var h=as[j].href;"
                + "if(/\\.(mp4|m3u8|mpd|webm|mkv|flv|mov|ts|mp3|m4a|aac|ogg|wav|flac|pdf|zip|rar|7z|apk|docx?|xlsx?|pptx?|epub|mobi)([?#]|$)/i.test(h)){"
                + "push(out,h,'file',{n:(as[j].textContent||'').trim().slice(0,60)})}}"
                + "var imgs=document.images||[];var n=Math.min(imgs.length,150);"
                + "for(var k=0;k<n;k++){var im=imgs[k];var src=im.currentSrc||im.src;"
                + "if(src)push(out,src,'image',{w:im.naturalWidth,h:im.naturalHeight})}"
                + "try{var rs=performance.getEntriesByType('resource')||[];"
                + "for(var m=0;m<rs.length;m++){var u=rs[m].name;"
                + "if(/\\.(mp4|m3u8|mpd|webm|mkv|flv|mov|ts|mp3|m4a|aac|ogg|wav|flac)([?#]|$)/i.test(u))push(out,u,'net',{})}}catch(e){}"
                + "say(out);}"
                + "scan();"
                + "var tm=0;try{new MutationObserver(function(){clearTimeout(tm);tm=setTimeout(scan,900)})"
                + ".observe(document.documentElement,{childList:true,subtree:true})}catch(e){}"
                + "setTimeout(scan,1500);setTimeout(scan,4000);"
                + "return 'ok'}catch(e){return 'err:'+e.message}})();";
    }

    // ================= 跳广告（保守：只点“跳过广告”类按钮） =================

    static String adSkip(String token) {
        return "(function(){try{"
                + "var TK=" + jsString(token) + ";"
                + "if(window.__zbAdSkip)return 'already';window.__zbAdSkip=1;"
                + "var RE=/(跳过广告|关闭广告|跳过此广告|跳过该广告|广告跳过|skip\\s*ad)/i;"
                + "function tap(){try{var els=document.querySelectorAll('button,a,div,span');"
                + "for(var i=0;i<els.length;i++){var el=els[i];if(el.__zbT)continue;"
                + "var t=(el.textContent||'').trim();if(!t||t.length>12)continue;if(!RE.test(t))continue;"
                + "if(el.offsetParent===null&&el.getClientRects().length===0)continue;"
                + "el.__zbT=1;el.click();try{ZBSniff.skipped(TK,t)}catch(e){}}}catch(e){}}"
                + "tap();try{new MutationObserver(tap).observe(document.documentElement,{childList:true,subtree:true})}catch(e){}"
                + "setInterval(tap,1500);return 'ok'}catch(e){return 'err'}})();";
    }

    // ================= 影院模式（接管页面里的 video） =================

    static String cinema(boolean adSkipOn, String token, long resumeMs) {
        return "(function(){try{"
                + "if(window.__zbCinema)return 'already';"
                + "var TK=" + jsString(token) + ";"
                + "var vs=[].slice.call(document.querySelectorAll('video'));"
                + "if(!vs.length)return 'novideo';"
                + "function rank(v){var s=v.paused?0:1e9;var d=isFinite(v.duration)?v.duration:0;"
                + "return s+d*1000+(v.clientWidth||0)*(v.clientHeight||0)/100;}"
                + "vs.sort(function(a,b){return rank(b)-rank(a)});"
                + "var v=vs[0];window.__zbCinema=v;"
                + "var pv={position:v.style.position,top:v.style.top,left:v.style.left,width:v.style.width,"
                + "height:v.style.height,zIndex:v.style.zIndex,objectFit:v.style.objectFit,"
                + "background:v.style.background,overflow:document.documentElement.style.overflow,"
                + "controls:v.controls};"
                + "v.controls=false;"
                + "v.style.position='fixed';v.style.top='0';v.style.left='0';"
                + "v.style.width='100vw';v.style.height='100vh';v.style.zIndex='2147483645';"
                + "v.style.objectFit='contain';v.style.background='#000';"
                + "document.documentElement.style.overflow='hidden';"
                + "var ui=document.createElement('div');ui.id='zb-cinema';"
                + "ui.innerHTML='<div class=\"zc-top\"><div class=\"zc-title\"></div>"
                + "<div class=\"zc-btns\"><button class=\"zc-b\" id=\"zcFwd\">+10s</button>"
                + "<button class=\"zc-b\" id=\"zcExit\">退出</button></div></div>"
                + "<div class=\"zc-center\"><button class=\"zc-play\" id=\"zcPlay\">▶</button></div>"
                + "<div class=\"zc-bot\"><span id=\"zcCur\">0:00</span>"
                + "<input id=\"zcSeek\" type=\"range\" min=\"0\" max=\"1000\" value=\"0\">"
                + "<span id=\"zcDur\">0:00</span>"
                + "<button class=\"zc-b\" id=\"zcRate\">1.0x</button>"
                + "<button class=\"zc-b\" id=\"zcMute\">声音</button></div>';"
                + "document.body.appendChild(ui);"
                + "var css='#zb-cinema{position:fixed;left:0;right:0;top:0;bottom:0;z-index:2147483646;"
                + "pointer-events:none;font-family:-apple-system,sans-serif;transition:opacity .25s}"
                + "#zb-cinema .zc-top,#zb-cinema .zc-bot,#zb-cinema .zc-center{pointer-events:auto}"
                + "#zb-cinema .zc-top{display:flex;align-items:center;gap:8px;padding:10px 12px;"
                + "background:linear-gradient(180deg,rgba(0,0,0,.72),rgba(0,0,0,0));color:#fff}"
                + "#zb-cinema .zc-title{flex:1;font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
                + "#zb-cinema .zc-b{background:rgba(255,255,255,.14);color:#fff;border:0;border-radius:8px;"
                + "padding:6px 10px;font-size:12px;margin-left:6px}"
                + "#zb-cinema .zc-center{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%)}"
                + "#zb-cinema .zc-bot,#zb-cinema .zc-center{transition:opacity .25s}"
                + "#zb-cinema .zc-play{width:64px;height:64px;border-radius:32px;border:0;"
                + "background:rgba(0,0,0,.45);color:#fff;font-size:22px}"
                + "#zb-cinema .zc-bot{position:absolute;left:0;right:0;bottom:0;display:flex;align-items:center;"
                + "gap:8px;padding:10px 12px 18px;background:linear-gradient(0deg,rgba(0,0,0,.72),rgba(0,0,0,0));color:#fff;font-size:12px}"
                + "#zb-cinema #zcSeek{flex:1;accent-color:#4C9AFF}';"
                + "var st=document.createElement('style');st.textContent=css;document.head.appendChild(st);"
                + "ui.querySelector('.zc-title').textContent=document.title||'';"
                + "var seek=ui.querySelector('#zcSeek'),cur=ui.querySelector('#zcCur'),dur=ui.querySelector('#zcDur'),"
                + "play=ui.querySelector('#zcPlay'),rate=ui.querySelector('#zcRate'),mute=ui.querySelector('#zcMute');"
                + "function fmt(t){t=Math.max(0,Math.floor(t||0));var m=Math.floor(t/60),s=t%60;"
                + "return m+':'+(s<10?'0':'')+s}"
                + "function sync(){var d=isFinite(v.duration)?v.duration:0;"
                + "cur.textContent=fmt(v.currentTime);dur.textContent=fmt(d);"
                + "seek.value=d?Math.round(v.currentTime/d*1000):0}"
                + "v.addEventListener('timeupdate',sync);v.addEventListener('durationchange',sync);sync();"
                + "seek.addEventListener('input',function(){if(isFinite(v.duration))v.currentTime=v.duration*seek.value/1000});"
                + "play.addEventListener('click',function(){if(v.paused){v.play()}else{v.pause()}});"
                + "v.addEventListener('play',function(){play.textContent='❙❙'});"
                + "v.addEventListener('pause',function(){play.textContent='▶'});"
                + "ui.querySelector('#zcFwd').addEventListener('click',function(){v.currentTime=Math.min((v.currentTime||0)+10,(v.duration||1e9))});"
                + "var rates=[1,1.25,1.5,2,0.75],ri=0;"
                + "rate.addEventListener('click',function(){ri=(ri+1)%rates.length;v.playbackRate=rates[ri];rate.textContent=rates[ri].toFixed(1)+'x'});"
                + "mute.addEventListener('click',function(){v.muted=!v.muted;mute.textContent=v.muted?'静音':'声音'});"
                + "var bot=ui.querySelector('.zc-bot'),cen=ui.querySelector('.zc-center');"
                + "var hideT=0;function show(){bot.style.opacity='1';cen.style.opacity='1';"
                + "clearTimeout(hideT);hideT=setTimeout(function(){bot.style.opacity='0';cen.style.opacity='0'},3200)}"
                + "document.addEventListener('touchstart',show,{passive:true});show();"
                + "play.textContent=v.paused?'▶':'❙❙';"
                + "if(" + resumeMs + ">3000&&isFinite(v.duration)&&" + resumeMs + "<v.duration*1000-5000){"
                + "try{v.currentTime=" + (resumeMs / 1000.0) + "}catch(e){}}"
                + "try{v.play()}catch(e){}"
                + "try{ZBSniff.cinema(TK,'1')}catch(e){}"
                + "ui.querySelector('#zcExit').addEventListener('click',function(){"
                + "v.style.position=pv.position;v.style.top=pv.top;v.style.left=pv.left;v.style.width=pv.width;"
                + "v.style.height=pv.height;v.style.zIndex=pv.zIndex;v.style.objectFit=pv.objectFit;"
                + "v.style.background=pv.background;document.documentElement.style.overflow=pv.overflow;"
                + "v.controls=pv.controls;"
                + "ui.remove();st.remove();window.__zbCinema=null;"
                + "try{ZBSniff.progress(TK,JSON.stringify({p:Math.round((v.currentTime||0)*1000),"
                + "d:Math.round((v.duration||0)*1000)}))}catch(e){}"
                + "try{ZBSniff.cinema(TK,'0')}catch(e){}});"
                + (adSkipOn
                    ? "var RE=/(跳过广告|关闭广告|跳过此广告|广告跳过|skip\\\\s*ad)/i;"
                      + "setInterval(function(){var els=document.querySelectorAll('button,a,div,span');"
                      + "for(var i=0;i<els.length;i++){var el=els[i];if(el.__zbT)continue;"
                      + "var t=(el.textContent||'').trim();if(!t||t.length>12||!RE.test(t))continue;"
                      + "if(el.offsetParent===null&&el.getClientRects().length===0)continue;"
                      + "el.__zbt=1;el.__zbT=1;el.click();}},1200);"
                    : "")
                + "return 'ok'}catch(e){return 'err:'+e.message}})();";
    }

    static String cinemaOff() {
        return "(function(){try{var b=document.querySelector('#zb-cinema #zcExit');"
                + "if(b){b.click();return 'ok'}return 'none'}catch(e){return 'err'}})();";
    }

}
