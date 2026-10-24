const hosts:Record<string,string[]>={BANGUMI:['bgm.tv','bangumi.tv','chii.in'],PROJECT_POLICY:['bgm.tv'],MOEGIRL:['zh.moegirl.org.cn','mzh.moegirl.org.cn','zh.moegirl.org'],ACDB:['animecharactersdatabase.com','www.animecharactersdatabase.com'],WIKIDATA:['wikidata.org','www.wikidata.org']}
export function reviewedNameSourceLink(provider:string,value:string):string|undefined{
  try{const url=new URL(value);if(url.protocol!=='https:'||url.username||url.password||url.port||(!hosts[provider]&&provider!=='PROJECT')||(hosts[provider]&&!hosts[provider]!.includes(url.hostname)))return undefined;return url.href}catch{return undefined}
}
