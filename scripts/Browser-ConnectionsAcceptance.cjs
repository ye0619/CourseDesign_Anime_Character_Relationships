const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs/promises'),path=require('node:path');
const output=path.resolve('.local/iteration-1024-2');
let browser;
(async()=>{
 await fs.mkdir(output,{recursive:true});
 browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 const context=await browser.newContext({viewport:{width:1440,height:960}}),page=await context.newPage();
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const records={checkedAt:new Date().toISOString(),checks:{},screenshots:[],fixtureCharts:true};
 const shot=async(name,locator)=>{const file=path.join(output,name+'.png');if(locator)await locator.screenshot({path:file,timeout:10000});else await page.screenshot({path:file,fullPage:true,timeout:10000});records.screenshots.push(file)};
 const api=async(endpoint,method='GET',body)=>page.evaluate(async({endpoint,method,body})=>{let headers={'Content-Type':'application/json'};if(method!=='GET'){const c=(await(await fetch('/api/v1/session/csrf')).json()).data;headers[c.headerName]=c.token}const response=await fetch('/api/v1'+endpoint+(endpoint.includes('?')?'&':'?')+'acceptance='+Date.now(),{cache:'no-store',method,headers,body:body===undefined?undefined:JSON.stringify(body)});const data=await response.json();if(!response.ok)throw new Error(response.status+':'+data.message);return data.data},{endpoint,method,body});
 const query='/connections?from=character%3Abangumi%3A119632&fromSubject=328609&to=character%3Abangumi%3A71478&toSubject=373267';
 await page.goto('http://127.0.0.1:5173'+query);await page.locator('.chosen').nth(1).waitFor();await page.getByRole('button',{name:'查找联系',exact:true}).click();await page.locator('.path-stage').waitFor({timeout:20000});
 const first=await page.locator('.stage-tools strong').innerText();if(!first.includes('2 步'))throw Error('Expected real two-step minimum: '+first);records.checks.realTwoHop=true;
 await page.waitForFunction(()=>document.querySelectorAll('.path-choices button').length>1,{timeout:20000});records.checks.multiplePaths=true;
 await page.locator('.search-options summary').click();await page.getByLabel('最多经过几步关系',{exact:true}).fill('7');await page.getByLabel('最多返回联系',{exact:true}).fill('5');
 await page.getByRole('button',{name:'查找联系',exact:true}).click();await page.locator('.path-stage').waitFor({timeout:20000});await page.reload();await page.locator('.chosen').nth(1).waitFor();
 if(await page.getByLabel('最多经过几步关系',{exact:true}).inputValue()!=='7'||await page.getByLabel('最多返回联系',{exact:true}).inputValue()!=='5')throw Error('Route options not restored');records.checks.routeOptionsRestored=true;
 await page.getByRole('button',{name:'查找联系',exact:true}).click();await page.locator('.path-stage').waitFor({timeout:20000});
 await page.locator('.path-link').first().click();await shot('connections-spatial',page.locator('.path-stage'));
 await page.getByRole('button',{name:'切换二维列表'}).click();await shot('connections-flat',page.locator('.path-stage'));
 await page.getByRole('button',{name:'切换空间卡片'}).click();
 await page.setViewportSize({width:390,height:844});await shot('connections-mobile');
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);if(overflow>2)throw Error('Mobile horizontal overflow '+overflow);records.checks.mobile=true;
 await page.emulateMedia({colorScheme:'dark',reducedMotion:'reduce'});await shot('connections-dark-mobile');records.checks.darkAndReducedMotion=true;
 await page.emulateMedia({colorScheme:'light',reducedMotion:'no-preference'});
 await page.goto('http://127.0.0.1:5173/connections?from=character%3Abangumi%3A171994&fromSubject=253&to=character%3Abangumi%3A137776&toSubject=899');await page.locator('.chosen').nth(1).waitFor();await page.getByRole('button',{name:'查找联系',exact:true}).click();await page.locator('.path-stage').waitFor({timeout:20000});const six=await page.locator('.stage-tools strong').innerText();if(!six.includes('6 步'))throw Error('Actual six-step case changed: '+six);records.checks.realSixHop=true;await shot('connections-six-step-mobile');
 await page.setViewportSize({width:1440,height:960});
 await page.goto('http://127.0.0.1:5173/explore/anime/328609/network?character=character%3Abangumi%3A87968');await page.locator('.source-editor>button').waitFor({timeout:20000});
 let authoredVersion;
 const uid='character:bangumi:87968',before=await api('/character-edits/'+uid);await fs.writeFile(path.join(output,'browser-character-before.json'),JSON.stringify(before,null,2));
 try{
  await page.locator('.source-editor>button').click();await page.locator('.crop-controls input[type=range]').first().waitFor();
  await page.waitForFunction(()=>{const n=document.querySelector('.crop-controls input[type=range]');return n&&!n.disabled},{timeout:20000});
  await page.getByLabel('备注',{exact:true}).fill('browser-acceptance-1024-2');
  await page.locator('.crop-controls input[type=range]').nth(1).evaluate(el=>{el.value='0.2';el.dispatchEvent(new Event('input',{bubbles:true}))});
  await shot('character-edit-desktop',page.locator('.character-edit-form'));
  const saving=page.waitForResponse(r=>r.url().includes('/character-edits/')&&r.request().method()==='PUT');
  await page.getByRole('button',{name:'保存角色修改',exact:true}).click();const response=await saving;if(response.status()!==200)throw Error('UI save failed: '+await response.text());await response.finished();authoredVersion=(await response.json()).data.version;
  const changed=await api('/character-edits/'+uid);await fs.writeFile(path.join(output,'browser-character-changed.json'),JSON.stringify(changed,null,2));if(!/^sha256:[a-f0-9]{64}$/.test(changed.portraits.GLOBAL?.imageFingerprint||''))throw Error('No content-bound shared portrait');records.checks.realCharacterCropSaved=true;
  await page.reload();await page.locator('.source-editor>button').waitFor();await page.locator('.source-editor>button').click();await page.waitForFunction(()=>document.querySelectorAll('.crop-controls input[type=range]')[1]?.value==='0.2');
  const y=await page.locator('.crop-controls input[type=range]').nth(1).inputValue();if(Math.abs(Number(y)-.2)>.01)throw Error('Shared crop not retained');records.checks.cropRetainedAfterReload=true;
  await page.locator('.character-edit-form select').filter({has:page.locator('option[value="anime:bangumi:328609"]')}).selectOption('anime:bangumi:328609');
  await page.getByLabel('头像（512KB以内）',{exact:true}).setInputFiles({name:'acceptance.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6kS8AAAAASUVORK5CYII=','base64')});
  await page.waitForFunction(()=>{const image=document.querySelector('.crop-preview img');return image?.src.startsWith('data:')&&image.complete&&document.querySelector('.crop-controls input[type=range]')?.disabled===false});
  await page.locator('.crop-controls input[type=range]').nth(1).evaluate(el=>{el.value='0.1';el.dispatchEvent(new Event('input',{bubbles:true}))});
  const savingWork=page.waitForResponse(r=>r.url().includes('/character-edits/')&&r.request().method()==='PUT');await page.getByRole('button',{name:'保存角色修改',exact:true}).click();const workResponse=await savingWork;if(workResponse.status()!==200)throw Error('Work avatar save failed: '+await workResponse.text());authoredVersion=(await workResponse.json()).data.version;
  const scoped=await api('/character-edits/'+uid);if(!scoped.portraits['anime:bangumi:328609']?.imageUrl?.startsWith('data:image/png')||scoped.fields.imageUrl?.startsWith('data:'))throw Error('Work avatar leaked into global fields');records.checks.workAvatarScope=true;

 }finally{const current=await api('/character-edits/'+uid);if(current.version!==before.version){if(current.version!==authoredVersion||current.fields.notes!=='browser-acceptance-1024-2')throw Error('Concurrent character edit detected; preserved current data instead of overwriting it');await api('/character-edits/'+uid,'PUT',{...before,version:current.version})}records.checks.characterRestored=true;}
 await page.goto('http://127.0.0.1:5173/connections');await page.locator('h1').waitFor();
 await page.evaluate(async()=>{
  const source=await(await fetch('/src/components/ReportChart.vue')).text();const vuePath=source.match(/from\s*["']([^"']*\/vue\.js[^"']*)["']/)?.[1];if(!vuePath)throw Error('Vue module path unavailable');
  const {createApp}=await import(vuePath),Chart=(await import('/src/components/ReportChart.vue')).default;
  const host=document.createElement('section');host.id='chart-acceptance';host.style.cssText='padding:25px;max-width:800px;background:white;color:#40594f';document.querySelector('main').append(host);
  createApp(Chart,{label:'浏览器验收样例数量（非个人画像）',items:[{id:'a',label:'样例甲',count:5},{id:'b',label:'样例乙',count:3},{id:'c',label:'样例丙',count:0}],onSelect:item=>window.__chartSelected=item}).mount(host);
 });
 await page.locator('#chart-acceptance canvas').waitFor();await page.locator('#chart-acceptance').getByRole('button',{name:'查看数据表 / 键盘选择'}).click();await page.getByRole('button',{name:'查看样例甲明细'}).click();
 const selected=await page.evaluate(()=>window.__chartSelected);if(selected.id!=='a'||selected.count!==5)throw Error('Chart selection count mismatch');records.checks.chartKeyboardDrilldown=true;await shot('chart-data-table',page.locator('#chart-acceptance'));
 if(process.env.REPORT_FIXTURE_FILE){const report=JSON.parse(await fs.readFile(process.env.REPORT_FIXTURE_FILE,'utf8'));await page.evaluate(async report=>{
  const source=await(await fetch('/src/components/ProfileBreakdown.vue')).text();const vuePath=source.match(/from\s*["']([^"']*\/vue\.js[^"']*)["']/)?.[1];const {createApp}=await import(vuePath),Profile=(await import('/src/components/ProfileBreakdown.vue')).default;
  const host=document.createElement('section');host.id='actual-report-acceptance';host.style.cssText='padding:24px;background:white;color:#40594f';document.querySelector('main').append(host);createApp(Profile,{report}).mount(host);
 },report);const companySection=page.locator('#actual-report-acceptance [aria-label="动画制作公司偏好"]');await companySection.locator('canvas').waitFor();if(await companySection.locator('.profile-row').count()!==report.companies.length)throw Error('Company report row count mismatch');await shot('actual-company-report',companySection);records.checks.actualCompanyReport=true;records.actualReportCompanies=report.companies.length;records.actualReportReadOnly=true;}
 records.pageErrors=errors;if(errors.length)throw Error('Browser errors: '+errors.join(';'));
 records.passed=true;await fs.writeFile(path.join(output,'browser-acceptance.json'),JSON.stringify(records,null,2));
 await browser.close();console.log(JSON.stringify({passed:true,checks:records.checks,screenshots:records.screenshots.length}));
})().catch(async e=>{console.error(e);await browser?.close();process.exitCode=1});
