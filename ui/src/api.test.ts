import {afterEach,expect,it,vi} from 'vitest';
import {api,rules} from './api';
afterEach(()=>vi.unstubAllGlobals());
it('keeps HTTP status rules explicit',()=>{expect(rules('sample.Reply','sample.Error')).toEqual({'2xx':'sample.Reply','4xx':'sample.Error','5xx':'sample.Error'});});
it('sends JSON management traffic independently of target protobuf',async()=>{
 const fetch=vi.fn().mockResolvedValue({ok:true,json:async()=>({size:2})});vi.stubGlobal('fetch',fetch);
 expect(await api('codec/encode',{protoJson:{revision:'9007199254740993'}})).toEqual({size:2});
 expect(fetch.mock.calls[0][0]).toBe('/local/v1/codec/encode');expect(fetch.mock.calls[0][1].body).toContain('"9007199254740993"');
});
it('surfaces API and non JSON failures',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:false,status:401,json:async()=>{throw Error();}}));await expect(api('codec/encode',{})).rejects.toThrow('HTTP 401');
});
