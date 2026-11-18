
(function(){
try{
  var D=__DATA__;
  if(document.getElementById('swa-root')) return;

  /* keep every link / window.open inside this view */
  window.open=function(u){ if(u){ location.href=u; } return null; };
  document.addEventListener('click',function(e){
    var a=e.target&&e.target.closest?e.target.closest('a[target]'):null;
    if(a&&a.getAttribute('target')!=='_self'){ a.setAttribute('target','_self'); }
  },true);

  var host=document.createElement('div');
  host.id='swa-root';
  host.style.cssText='all:initial;position:fixed;right:0;bottom:0;width:0;height:0;z-index:2147483647;';
  var root=host.attachShadow({mode:'open'});
  var CSS=`
  .fab{position:fixed;right:25px;bottom:25px;width:60px;height:60px;border-radius:50%;border:0;cursor:pointer;font-size:26px;color:#fff;
    background:linear-gradient(135deg,#1D4ED8,#38BDF8);box-shadow:0 4px 15px rgba(29,78,216,.45);display:grid;place-items:center;
    transition:all .3s cubic-bezier(.25,.8,.25,1);padding:0;line-height:1}
  .fab:hover{transform:scale(1.06)}
  .fab.open{transform:rotate(90deg);background:#0B0B0F;box-shadow:0 4px 15px rgba(0,0,0,.4)}
  .panel{position:fixed;right:25px;bottom:97px;width:240px;max-height:70vh;overflow:auto;background:#fff;color:#0B0B0F;border:1px solid #E6E9F0;
    border-radius:18px;padding:8px;box-shadow:0 18px 40px rgba(0,0,0,.28);font:14px "Segoe UI",system-ui,sans-serif;opacity:0;
    transform:translateY(14px) scale(.96);transform-origin:bottom right;pointer-events:none;transition:all .3s cubic-bezier(.25,.8,.25,1)}
  .panel.open{opacity:1;transform:none;pointer-events:auto}
  .t{padding:8px 10px 6px;font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:#6B7280;font-weight:700}
  .r{display:flex;align-items:center;gap:10px;padding:9px 10px;border-radius:12px;cursor:pointer;transition:all .3s cubic-bezier(.25,.8,.25,1)}
  .r:hover{background:#F4F6FA}.r.on{background:#EAF0FF}
  .d{width:32px;height:32px;border-radius:10px;display:grid;place-items:center;color:#fff;font-weight:700;font-size:14px;flex:none;box-sizing:border-box}
  .d.logo{background:#fff;border:1px solid #E6E9F0}
  .d svg{width:20px;height:20px;display:block}
  .d.act svg{width:18px;height:18px}
  .n{flex:1;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .sep{height:1px;background:#E6E9F0;margin:6px 4px}
  .add .n{color:#1D4ED8}
  `;
  try{ var sh=new CSSStyleSheet(); sh.replaceSync(CSS); root.adoptedStyleSheets=[sh]; }
  catch(e){ var st=document.createElement('style'); st.textContent=CSS; root.appendChild(st); }

  function call(name,arg){
    var u='get3://';
    if(name==='open_platform') u+='open/'+encodeURIComponent(arg);
    else if(name==='go_home') u+=(arg==='add'?'add':'home');
    else if(name==='go_back') u+='back';
    else return;
    location.href=u;
  }
  function isActive(p){
    try{
      var h=new URL(p.url).hostname.replace(/^(www|m)\./,'');
      var cur=location.hostname.replace(/^(www|m)\./,'');
      return cur===h||cur.indexOf('.'+h)>-1;
    }catch(e){return false}
  }
  var NS='http://www.w3.org/2000/svg';
  function S(tag,attrs,kids){
    var e=document.createElementNS(NS,tag);
    for(var k in attrs){ e.setAttribute(k,attrs[k]); }
    (kids||[]).forEach(function(c){ e.appendChild(c); });
    return e;
  }
  function line(path){   /* clean stroke icon (white) */
    return S('svg',{viewBox:'0 0 24 24',fill:'none',stroke:'#fff','stroke-width':'2.2','stroke-linecap':'round','stroke-linejoin':'round'},[S('path',{d:path})]);
  }
  function brandLogo(id){
    if(id==='youtube') return S('svg',{viewBox:'0 0 24 24'},[
      S('rect',{x:1,y:4.5,width:22,height:15,rx:4.5,fill:'#FF0000'}),
      S('path',{d:'M10 9l5.5 3L10 15z',fill:'#fff'})]);
    if(id==='instagram') return S('svg',{viewBox:'0 0 24 24'},[
      S('defs',{},[S('linearGradient',{id:'swaIg',x1:0,y1:1,x2:1,y2:0},[
        S('stop',{offset:0,'stop-color':'#FEDA75'}),S('stop',{offset:.4,'stop-color':'#FA7E1E'}),
        S('stop',{offset:.7,'stop-color':'#D62976'}),S('stop',{offset:1,'stop-color':'#4F5BD5'})])]),
      S('rect',{x:1,y:1,width:22,height:22,rx:6.5,fill:'url(#swaIg)'}),
      S('rect',{x:6,y:6,width:12,height:12,rx:4,fill:'none',stroke:'#fff','stroke-width':1.8}),
      S('circle',{cx:12,cy:12,r:2.9,fill:'none',stroke:'#fff','stroke-width':1.8}),
      S('circle',{cx:16.6,cy:7.4,r:1.1,fill:'#fff'})]);
    if(id==='facebook') return S('svg',{viewBox:'0 0 24 24'},[
      S('circle',{cx:12,cy:12,r:11,fill:'#1877F2'}),
      S('path',{d:'M13.5 21v-7h2.4l.4-3h-2.8V9.3c0-.8.3-1.3 1.4-1.3h1.5V5.3C16.2 5.2 15.3 5 14.3 5 12.2 5 10.8 6.3 10.8 8.6V11H8.4v3h2.4v7z',fill:'#fff'})]);
    return null;
  }
  function row(iconNode,color,label,cls,fn){
    var r=document.createElement('div'); r.className='r '+(cls||'');
    var d=document.createElement('div');
    if(iconNode.__logo){ d.className='d logo'; }
    else if(iconNode.__act){ d.className='d act'; d.style.background=color; }
    else { d.className='d'; d.style.background=color; }
    d.appendChild(iconNode.node);
    var n=document.createElement('div'); n.className='n'; n.textContent=label;
    r.appendChild(d); r.appendChild(n);
    r.addEventListener('click',function(e){ e.stopPropagation(); close(); fn(); });
    return r;
  }

  var fab=document.createElement('button'); fab.className='fab'; fab.textContent='\u26A1'; fab.setAttribute('aria-label','Switch platform');
  var panel=document.createElement('div'); panel.className='panel';
  var t=document.createElement('div'); t.className='t'; t.textContent='Switch platform'; panel.appendChild(t);
  D.platforms.forEach(function(p){
    var logo=brandLogo(p.id), ic;
    if(logo){ ic={node:logo,__logo:true}; }
    else { var tx=document.createElement('span'); tx.textContent=(p.name||'?').charAt(0).toUpperCase(); ic={node:tx}; }
    panel.appendChild(row(ic,p.color||'#1D4ED8',p.name,isActive(p)?'on':'',function(){call('open_platform',p.id)}));
  });
  var sep=document.createElement('div'); sep.className='sep'; panel.appendChild(sep);
  panel.appendChild(row({node:line('M3 11.5L12 4l9 7.5M5.5 10v9.5a.5.5 0 0 0 .5.5h3.5v-5.5h5V20H18a.5.5 0 0 0 .5-.5V10'),__act:true},'linear-gradient(135deg,#0B0B0F,#2B2F3A)','Home','',function(){call('go_home')}));
  panel.appendChild(row({node:line('M19 12H6M11.5 6.5L6 12l5.5 5.5'),__act:true},'linear-gradient(135deg,#475569,#64748B)','Back','',function(){call('go_back')}));
  panel.appendChild(row({node:line('M12 5.5v13M5.5 12h13'),__act:true},'linear-gradient(135deg,#1D4ED8,#38BDF8)','Add Platform','add',function(){call('go_home','add')}));

  function open(){ panel.classList.add('open'); fab.classList.add('open'); }
  function close(){ panel.classList.remove('open'); fab.classList.remove('open'); }
  fab.addEventListener('click',function(e){ e.stopPropagation(); panel.classList.contains('open')?close():open(); });
  document.addEventListener('click',function(e){ if(!host.contains(e.target)) close(); },true);

  root.appendChild(panel); root.appendChild(fab);
  document.documentElement.appendChild(host);

  /* single-page apps may rebuild the DOM: re-attach if removed */
  setInterval(function(){ if(!document.getElementById('swa-root')){ document.documentElement.appendChild(host); } },2000);
}catch(err){}
})();
