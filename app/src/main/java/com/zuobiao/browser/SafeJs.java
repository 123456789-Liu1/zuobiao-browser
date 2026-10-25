package com.zuobiao.browser;

/**
 * 安全模式（严格档）的注入脚本与自检页。
 *
 * 与 Js.java 分开：这里只在 document start 执行一次，负责
 * ① 伪装设备/浏览器指纹；② 拒绝敏感 API；③ 加指纹噪声。
 */
final class SafeJs {

    private SafeJs() {}

    /** 在 document start 注入；token 用于 ZBSafe 桥校验。 */
    static String strict(String token) {
        String js = """
                (function(){
                try{
                  if(window.__zbSafe) return 'already';
                  window.__zbSafe = 1;
                  var TK = '__TK__';
                  function bump(api){ try{ ZBSafe.blocked(TK, api); }catch(e){} }
                  function def(o,p,v){ try{ Object.defineProperty(o,p,{get:function(){return v;},configurable:true}); }catch(e){} }
                  function defFn(o,p,f){ try{ Object.defineProperty(o,p,{value:f,configurable:true,writable:true}); }catch(e){} }

                  var NAV = navigator;
                  var UA = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36';
                  def(NAV,'userAgent',UA); bump('userAgent');
                  def(NAV,'appVersion',UA.replace('Mozilla/',''));
                  def(NAV,'platform','Linux armv8l');
                  def(NAV,'vendor','Google Inc.');
                  def(NAV,'productSub','20030107');
                  def(NAV,'hardwareConcurrency',4);
                  def(NAV,'deviceMemory',4);
                  def(NAV,'maxTouchPoints',5);
                  def(NAV,'language','zh-CN');
                  def(NAV,'languages',['zh-CN','zh']);
                  def(NAV,'doNotTrack','1');
                  def(NAV,'webdriver',false);
                  def(NAV,'connection',{effectiveType:'4g',downlink:10,rtt:100,saveData:false,onchange:null,
                    addEventListener:function(){},removeEventListener:function(){}});
                  try{
                    Object.defineProperty(NAV,'userAgentData',{configurable:true,get:function(){
                      return {brands:[],mobile:true,platform:'Android',
                        getHighEntropyValues:function(){bump('ua-ch');return Promise.resolve({});},
                        toJSON:function(){return {};}};
                    }});
                  }catch(e){}

                  defFn(NAV,'getBattery',function(){bump('battery');return Promise.resolve({
                    charging:true,chargingTime:0,dischargingTime:Infinity,level:1,
                    addEventListener:function(){},removeEventListener:function(){}});});

                  if(NAV.permissions) defFn(NAV.permissions,'query',function(){bump('permissions');return Promise.resolve({
                    state:'denied',onchange:null,addEventListener:function(){},removeEventListener:function(){}});});
                  if(NAV.storage) defFn(NAV.storage,'estimate',function(){bump('storage');return Promise.resolve({quota:104857600,usage:0});});
                  if(NAV.mediaDevices){
                    defFn(NAV.mediaDevices,'enumerateDevices',function(){bump('devices');return Promise.resolve([]);});
                    defFn(NAV.mediaDevices,'getUserMedia',function(){bump('getUserMedia');return Promise.reject(new Error('NotAllowedError'));});
                  }
                  if(NAV.geolocation){
                    defFn(NAV.geolocation,'getCurrentPosition',function(ok,err){bump('geo');if(err)err({code:1,message:'denied'});});
                    defFn(NAV.geolocation,'watchPosition',function(ok,err){bump('geo');if(err)err({code:1,message:'denied'});return 0;});
                  }
                  if(NAV.clipboard){
                    defFn(NAV.clipboard,'readText',function(){bump('clipboard');return Promise.reject(new Error('NotAllowedError'));});
                    if(NAV.clipboard.read) defFn(NAV.clipboard,'read',function(){bump('clipboard');return Promise.reject(new Error('NotAllowedError'));});
                  }
                  if(NAV.sendBeacon) defFn(NAV,'sendBeacon',function(){bump('beacon');return false;});

                  ['DeviceOrientationEvent','DeviceMotionEvent','AbsoluteOrientationSensor','Accelerometer','Gyroscope','Magnetometer'].forEach(function(k){
                    try{ delete window[k]; }catch(e){}
                    try{ Object.defineProperty(window,k,{value:undefined,configurable:true}); }catch(e){}
                  });
                  ['bluetooth','usb','serial','hid','gpu'].forEach(function(k){ try{ delete NAV[k]; }catch(e){} });
                  ['RTCPeerConnection','webkitRTCPeerConnection','RTCDataChannel'].forEach(function(k){
                    try{ Object.defineProperty(window,k,{value:undefined,configurable:true}); }catch(e){}
                  });

                  try{ Object.defineProperty(Notification,'permission',{get:function(){return 'denied';},configurable:true}); }catch(e){}
                  try{ Notification.requestPermission=function(){bump('notification');return Promise.resolve('denied');}; }catch(e){}

                  var SC={width:412,height:915,availWidth:412,availHeight:915,colorDepth:24,pixelDepth:24};
                  Object.keys(SC).forEach(function(k){ def(screen,k,SC[k]); });
                  def(window,'devicePixelRatio',2.625);

                  try{
                    function wrap(proto){
                      var orig=proto.getParameter;
                      proto.getParameter=function(p){
                        if(p===37445||p===7936){bump('webgl');return 'Qualcomm';}
                        if(p===37446||p===7937){bump('webgl');return 'Adreno (TM) 640';}
                        return orig.apply(this,arguments);
                      };
                    }
                    if(window.WebGLRenderingContext) wrap(WebGLRenderingContext.prototype);
                    if(window.WebGL2RenderingContext) wrap(WebGL2RenderingContext.prototype);
                  }catch(e){}

                  try{
                    var toDataURL=HTMLCanvasElement.prototype.toDataURL;
                    HTMLCanvasElement.prototype.toDataURL=function(){
                      try{
                        var ctx=this.getContext('2d');
                        if(ctx&&this.width&&this.height){
                          var d=ctx.getImageData(0,0,Math.min(this.width,8),Math.min(this.height,8));
                          for(var i=0;i<d.data.length;i+=97){d.data[i]=(d.data[i]+1)&255;}
                          ctx.putImageData(d,0,0);
                        }
                        bump('canvas');
                      }catch(e){}
                      return toDataURL.apply(this,arguments);
                    };
                    var gid=CanvasRenderingContext2D.prototype.getImageData;
                    CanvasRenderingContext2D.prototype.getImageData=function(){
                      var r=gid.apply(this,arguments);
                      try{ for(var i=0;i<r.data.length;i+=997){r.data[i]=(r.data[i]+1)&255;} }catch(e){}
                      return r;
                    };
                  }catch(e){}

                  try{
                    if(window.AnalyserNode){
                      var gff=AnalyserNode.prototype.getFloatFrequencyData;
                      AnalyserNode.prototype.getFloatFrequencyData=function(arr){
                        gff.apply(this,arguments);
                        try{ for(var i=0;i<arr.length;i+=31){arr[i]+=0.0001;} bump('audio'); }catch(e){}
                      };
                    }
                  }catch(e){}

                  return 'ok';
                }catch(e){ return 'err:'+e.message; }
                })();
                """;
        return js.replace("'__TK__'", "'" + token + "'");
    }

    /** 内置隐私自检页：把当前浏览器指纹摊开给用户看。 */
    static String checkPage(int blocked) {
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>body{margin:0;padding:16px;background:#0E1116;color:#E6EDF3;"
                + "font:13px/1.7 -apple-system,'PingFang SC',sans-serif}"
                + "h2{font-size:16px;margin:0 0 10px}p{color:#8B949E;margin:6px 0 14px}"
                + ".row{display:flex;justify-content:space-between;gap:12px;padding:7px 0;"
                + "border-bottom:1px solid #232B36}.k{color:#8B949E;flex:0 0 42%}"
                + ".v{color:#E6EDF3;word-break:break-all;text-align:right;flex:1}"
                + ".badge{display:inline-block;padding:2px 8px;border-radius:10px;"
                + "background:#16324F;color:#CFE4FF;font-size:12px;margin-bottom:12px}</style></head>"
                + "<body><h2>隐私自检</h2>"
                + "<div class=\"badge\">本页已拦截 API 调用：" + blocked + " 次</div>"
                + "<p>安全模式开启时，下面应是伪装后的通用值；关闭时应能看到真实设备信息。</p>"
                + "<div id=\"list\"></div>"
                + "<script>"
                + "var rows=[];"
                + "function add(k,v){rows.push([k,String(v)]);}"
                + "add('User-Agent',navigator.userAgent);"
                + "add('platform',navigator.platform);"
                + "add('vendor',navigator.vendor);"
                + "add('languages',(navigator.languages||[]).join(','));"
                + "add('hardwareConcurrency',navigator.hardwareConcurrency);"
                + "add('deviceMemory',navigator.deviceMemory);"
                + "add('maxTouchPoints',navigator.maxTouchPoints);"
                + "add('screen',screen.width+'x'+screen.height);"
                + "add('devicePixelRatio',window.devicePixelRatio);"
                + "add('timezone',Intl.DateTimeFormat().resolvedOptions().timeZone);"
                + "add('connection',navigator.connection?navigator.connection.effectiveType:'-');"
                + "add('geolocation',navigator.geolocation?'present':'absent');"
                + "add('mediaDevices',navigator.mediaDevices?'present':'absent');"
                + "add('RTCPeerConnection',typeof RTCPeerConnection);"
                + "add('DeviceOrientation',typeof DeviceOrientationEvent);"
                + "try{var c=document.createElement('canvas');var gl=c.getContext('webgl');"
                + "var d=gl.getExtension('WEBGL_debug_renderer_info');"
                + "add('WebGL renderer',d?gl.getParameter(d.UNMASKED_RENDERER_WEBGL):'hidden');}"
                + "catch(e){add('WebGL renderer','-');}"
                + "var h='';rows.forEach(function(r){h+='<div class=\"row\"><span class=\"k\">'+r[0]"
                + "+'</span><span class=\"v\">'+r[1]+'</span></div>';});"
                + "document.getElementById('list').innerHTML=h;"
                + "</script></body></html>";
    }
}
