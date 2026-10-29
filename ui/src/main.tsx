import React, {useEffect, useState} from 'react';
import {createRoot} from 'react-dom/client';
import {api, connect, rules} from './api';
import './style.css';
function App() {
  const [ready,setReady]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const [schema,setSchema]=useState(''),[filename,setFilename]=useState(''),[types,setTypes]=useState<string[]>([]);
  const [requestType,setRequestType]=useState(''),[responseType,setResponseType]=useState(''),[errorType,setErrorType]=useState('');
  const [json,setJson]=useState('{\n  "text": "Hello, Protobuf",\n  "revision": "9007199254740993"\n}');
  const [base64,setBase64]=useState(''),[url,setUrl]=useState('https://example.com/api/echo'),[headers,setHeaders]=useState('{}');
  const [allowHttp,setAllowHttp]=useState(false),[timeout,setTimeout]=useState(10000),[output,setOutput]=useState<any>(null),[tab,setTab]=useState('json');
  useEffect(()=>{connect().then(()=>setReady(true)).catch(e=>setError(e.message));},[]);
  async function run(action:()=>Promise<void>) {setBusy(true);setError('');try {await action();}catch(e){setError(e instanceof Error?e.message:'처리 실패');}finally{setBusy(false);}}
  async function load(file:File) {
    await run(async()=>{const bytes=new Uint8Array(await file.arrayBuffer());if(bytes.length>10*1024*1024)throw new Error('최대 10MiB입니다.');
      let raw='';for(const byte of bytes)raw+=String.fromCharCode(byte);
      const result=await api('schema/register',{descriptorBase64:btoa(raw)});
      setSchema(result.schemaId);setFilename(file.name);setTypes(result.types);setRequestType(result.types[0]||'');setResponseType(result.types[0]||'');setErrorType('');setBase64('');setOutput(null);
    });
  }
  async function encode() {const result=await api('codec/encode',{schemaId:schema,messageType:requestType,protoJson:JSON.parse(json)});setBase64(result.payloadBase64);setOutput(result);return result.payloadBase64;}
  const disabled=!ready||busy;
  const visibleResult = output?.protoJson ?? (output?.http ? '디코딩된 메시지가 없습니다. Raw Base64와 Headers에서 원본 응답을 확인하세요.' : output);

  function typeSelect(label:string,value:string,set:(v:string)=>void,optional=false){return <label>{label}<select value={value} onChange={e=>set(e.target.value)} disabled={disabled||!schema}>{optional&&<option value="">미지정</option>}{types.map(t=><option key={t}>{t}</option>)}</select></label>;}
  return <><header><div className="brand"><span className="mark">p</span><div>mj_proto_assist<small>LOCAL PROTOBUF WORKBENCH</small></div></div><span className="badge">● {ready?'로컬 세션 연결됨':'연결 필요'}</span></header>
    <main><div className="intro"><div className="eyebrow">HTTP + PROTOBUF</div><h1>바이너리 너머의 메시지.</h1><p>스키마를 불러오고, JSON으로 작성하고, 응답을 확인하세요.</p></div>
    {error&&<div className="error" role="alert">{error}</div>}
    <section className="schema"><div><b>01 · Schema</b><p>{filename||'imports를 포함한 FileDescriptorSet (.pb)을 선택하세요.'}</p>{schema&&<code title={schema}>{schema.slice(0,20)}… · {types.length} types</code>}</div><label className="file">스키마 불러오기<input aria-label="descriptor 업로드" type="file" accept=".pb,.bin,.desc" disabled={disabled} onChange={e=>{if(e.target.files?.[0])void load(e.target.files[0]);e.target.value='';}}/></label></section>
    <div className="columns"><section className="panel"><div className="section-title"><b>02 · Request</b><span>POST</span></div>
    {typeSelect('요청 메시지 타입',requestType,setRequestType)}
    <label>ProtoJSON<textarea disabled={disabled} spellCheck={false} value={json} onChange={e=>{setJson(e.target.value);setBase64('');}} rows={12}/></label>
    <div className="actions"><button disabled={disabled||!requestType} onClick={()=>void run(async()=>{await encode();})}>인코딩</button><button className="secondary" disabled={disabled||!responseType} onClick={()=>void run(async()=>{setOutput(await api('codec/decode',{schemaId:schema,messageType:responseType,payloadBase64:base64}));})}>Base64 디코딩</button></div>
    <label>Protobuf · Base64<textarea disabled={disabled} aria-label="Protobuf Base64" rows={3} value={base64} onChange={e=>setBase64(e.target.value)} spellCheck={false}/></label><p className="hint">전송 시 현재 ProtoJSON을 다시 인코딩합니다. Base64는 오프라인 디코딩에도 사용할 수 있습니다.</p>
    </section><section className="panel"><div className="section-title"><b>03 · HTTP target</b><span>명시적 실행</span></div>
    <label>대상 URL<input value={url} onChange={e=>setUrl(e.target.value)}/></label>
    <div className="type-grid">{typeSelect('성공 응답 · 2xx',responseType,setResponseType)}{typeSelect('오류 응답 · 4xx / 5xx',errorType,setErrorType,true)}</div>
    <label>헤더 · JSON<textarea rows={4} value={headers} onChange={e=>setHeaders(e.target.value)} spellCheck={false}/></label>
    <label>타임아웃 · ms<input type="number" min={100} max={60000} value={timeout} onChange={e=>setTimeout(Number(e.target.value))}/></label>
    <label className="check"><input type="checkbox" checked={allowHttp} onChange={e=>setAllowHttp(e.target.checked)}/>개발용 HTTP 전송 허용</label>
    <div className="target"><small>전송 대상 · POST</small><code>{url}</code></div>
    <button className="send" disabled={disabled||!requestType} onClick={()=>void run(async()=>{const payload=await encode();setOutput(await api('http/execute',{url,headers:JSON.parse(headers),timeoutMs:timeout,allowHttp,payloadBase64:payload,schemaId:schema,responseRules:rules(responseType,errorType)}));})}>{busy?'처리 중…':'POST 요청 보내기 →'}</button>
    <p className="hint">자동 재시도와 리다이렉트 추적을 하지 않습니다. 입력과 인증정보는 자동 저장하지 않습니다.</p></section></div>
    <section className="panel result"><div className="section-title"><b>04 · Response</b>{output?.http&&<span>HTTP {output.http.status} · {output.http.elapsedMs} ms</span>}</div><div className="tabs"><button className={tab==='json'?'active':''} onClick={()=>setTab('json')}>JSON / 결과</button><button className={tab==='raw'?'active':''} onClick={()=>setTab('raw')}>Raw Base64</button><button className={tab==='headers'?'active':''} onClick={()=>setTab('headers')}>Headers</button></div>
    {output?.messageType&&<p className="hint">선택한 응답 타입: {output.messageType}</p>}
    {output?.warnings?.length>0&&<p role="status">{output.warnings.join(', ')}</p>}
    {output?.decodeError&&<p className="error">응답 디코딩 실패 · 원본 바이너리는 Raw Base64에서 확인할 수 있습니다.</p>}
    <pre>{output?tab==='raw'?(output.http?.payloadBase64??output.payloadBase64??'바이너리 없음'):tab==='headers'?JSON.stringify(output.http?.headers??{},null,2):(typeof visibleResult==='string'?visibleResult:JSON.stringify(visibleResult,null,2)):'응답이 여기에 표시됩니다. 오프라인 변환은 네트워크를 호출하지 않습니다.'}</pre></section>
    <footer>mj_proto_assist · 초기 구현 · 일반 HTTP Protobuf 전용</footer></main></>;
}
createRoot(document.getElementById('root')!).render(<App/>);
