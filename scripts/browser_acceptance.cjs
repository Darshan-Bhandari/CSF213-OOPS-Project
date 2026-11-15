/** Optional localhost-only browser acceptance. Requires Playwright; credentials never appear in output. */
const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.BROWSER_BASE_URL || 'http://127.0.0.1:8081';
assert(['127.0.0.1','localhost','[::1]'].includes(new URL(base).hostname), 'Use a local disposable demo app');
const password = process.env.DEMO_PASSWORD || (process.env.DEMO_CREDENTIALS_FILE && JSON.parse(fs.readFileSync(process.env.DEMO_CREDENTIALS_FILE,'utf8')).password);
assert(password, 'Set DEMO_PASSWORD or DEMO_CREDENTIALS_FILE for your disposable demo app');
const output = path.resolve(process.env.BROWSER_OUTPUT || '.local/acceptance-screenshots');
fs.mkdirSync(output,{recursive:true});
const failures=[];
async function screen(page,name) {
  await page.screenshot({path:path.join(output,name+'.png'),fullPage:true});
  const width=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));
  assert(width.document<=width.viewport+1, `${name} has horizontal page overflow: ${JSON.stringify(width)}`);
}
(async()=>{
  const browser=await chromium.launch({headless:true});
  try {
    const pages={};
    for(const role of ['employee','technician','admin']) {
      const context=await browser.newContext({viewport:{width:1440,height:1000}});
      const page=await context.newPage();pages[role]=page;
      page.on('pageerror',e=>failures.push(e.message));
      page.on('response',r=>{if(r.status()>=500) failures.push(`${r.status()} ${r.url()}`);});
      await page.goto(base+'/login');
      await page.getByLabel('Email',{exact:true}).fill(role+'@campus.test');
      await page.getByLabel('Password',{exact:true}).fill(password);
      await page.getByRole('button',{name:/Sign in/}).click();
      await page.waitForURL('**/'+role+'/dashboard');
      await screen(page,role+'-desktop-dashboard');
      console.log('PASS '+role+' credential login and desktop dashboard');
    }
    const {employee,technician,admin}=pages;
    for(const route of ['/admin/users','/admin/catalog','/admin/audit','/admin/email']) {
      const response=await admin.goto(base+route);assert.equal(response.status(),200,route);
      assert.equal(await admin.locator('.nav-item.selected').getAttribute('href'),route,'Navigation must identify the active admin page');
      await screen(admin,'admin-desktop-'+route.split('/').pop());
    }
    await employee.goto(base+'/employee/incidents/new');
    await employee.locator('[name=title]').fill('Browser acceptance Wi-Fi incident');
    await employee.locator('[name=description]').fill('Disposable browser fixture: library Wi-Fi cannot connect.');
    await employee.locator('[name=service]').selectOption({label:'Campus Wi-Fi'});
    await employee.locator('[name=category]').selectOption({label:'Network'});
    await employee.locator('[name=impact]').selectOption('2');
    await employee.locator('[name=urgency]').selectOption('2');
    await employee.locator('[name=affected]').fill('10');
    await screen(employee,'employee-desktop-report-form');
    await employee.getByRole('button',{name:/Submit incident/}).click();
    await employee.waitForURL(/\/incidents\/\d+$/);
    const incident=employee.url();const id=incident.split('/').pop();
    const detail=async page=>{const r=await page.request.get(base+'/api/incidents/'+id);assert.equal(r.status(),200);return r.json();};
    assert.equal((await detail(employee)).priority,'P2');
    assert.equal((await detail(employee)).score,12);
    await technician.goto(incident);
    await technician.getByRole('button',{name:'Acknowledge assignment',exact:true}).click();
    await technician.getByRole('button',{name:'Start investigation',exact:true}).click();
    const before=await detail(technician);
    await technician.locator('select[name=kind]').selectOption('INTERNAL');
    await technician.locator('[name=body]').fill('Private browser diagnostic note');
    await technician.getByRole('button',{name:'Add message',exact:true}).click();
    await technician.getByText('Private browser diagnostic note',{exact:true}).waitFor();
    await technician.locator('select[name=kind]').selectOption('REQUEST');
    await technician.locator('[name=body]').fill('Which library floor and device?');
    await technician.getByRole('button',{name:'Add message',exact:true}).click();
    await employee.reload();
    assert.equal(await employee.getByText('Private browser diagnostic note',{exact:true}).count(),0);
    await employee.getByText('Which library floor and device?',{exact:true}).waitFor();
    const request=await employee.locator('[name=responseTo] option:not([value=""])').first().getAttribute('value');
    await employee.locator('[name=responseTo]').selectOption(request);
    await employee.locator('[name=body]').fill('Library first floor, campus laptop');
    await employee.getByRole('button',{name:'Add message',exact:true}).click();
    await technician.reload();
    await technician.getByText('Library first floor, campus laptop',{exact:true}).waitFor();
    const after=await detail(technician);
    for(const field of ['status','responseDue','resolutionDue']) assert.equal(after[field],before[field]);
    await screen(technician,'technician-desktop-investigation');
    await technician.locator('[name=details]').fill('First browser-tested Wi-Fi fix');
    await technician.getByRole('button',{name:'Resolve incident',exact:true}).click();
    await employee.reload();
    await employee.locator('[name=details]').fill('Still cannot reconnect after the first fix');
    await employee.getByRole('button',{name:'Reject resolution',exact:true}).click();
    assert.equal((await detail(employee)).status,'ASSIGNED');
    await technician.reload();
    await technician.getByRole('button',{name:'Acknowledge assignment',exact:true}).click();
    await technician.getByRole('button',{name:'Start investigation',exact:true}).click();
    await technician.locator('[name=details]').fill('Second browser-tested fix restored connectivity');
    await technician.getByRole('button',{name:'Resolve incident',exact:true}).click();
    await employee.reload();
    await employee.getByRole('button',{name:'Confirm and close',exact:true}).click();
    await employee.locator('[name=rating]').selectOption('5');
    await employee.locator('[name=comment]').fill('Confirmed working after the second fix');
    await employee.getByRole('button',{name:'Submit feedback',exact:true}).click();
    assert.equal((await detail(employee)).status,'CLOSED');
    await screen(employee,'employee-desktop-closed-incident');
    for(const [role,page] of Object.entries(pages)) {
      await page.setViewportSize({width:390,height:844});
      await page.goto(base+'/'+role+'/dashboard');
      assert(await page.getByRole('button',{name:'Sign out',exact:true}).isVisible(),role+' mobile sign-out must be visible');
      await screen(page,role+'-mobile-dashboard');
      if(role==='admin') {await page.goto(base+'/admin/catalog');await screen(page,'admin-mobile-catalog');}
      if(role==='employee') {await page.goto(incident);await screen(page,'employee-mobile-closed-incident');}
      await page.getByRole('button',{name:'Sign out',exact:true}).click();
      await page.waitForURL(/\/login/);
      console.log('PASS '+role+' responsive viewport and mobile sign-out');
    }
    assert.deepEqual(failures,[], 'No JavaScript exceptions or server 5xx responses');
    fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({passed:true,incident:id,viewports:['1440x1000','390x844'],screenshots:fs.readdirSync(output).filter(n=>n.endsWith('.png')),errors:failures},null,2));
    console.log('PASS browser lifecycle, privacy, unchanged deadlines, rejection/re-resolution, closure and feedback');
  } finally {await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});
