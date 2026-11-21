from pathlib import Path
import json,re,html,math,hashlib
from reportlab.platypus import SimpleDocTemplate,Paragraph,Spacer,Table,TableStyle,PageBreak,Flowable,KeepTogether
from reportlab.lib.styles import getSampleStyleSheet,ParagraphStyle
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
ROOT=Path(__file__).resolve().parent
BASE=ROOT.parents[2]
OUT=BASE/'artifacts'/'BITSOps_Project_Report_Refined.pdf'
W=487
styles=getSampleStyleSheet()
styles.add(ParagraphStyle(name='BodyR',fontName='Helvetica',fontSize=10.3,leading=14.8,spaceAfter=9))
styles.add(ParagraphStyle(name='SmallR',fontName='Helvetica',fontSize=9,leading=12,spaceAfter=5))
styles.add(ParagraphStyle(name='CaptionR',fontName='Helvetica-Oblique',fontSize=9,leading=12,spaceAfter=10))
styles.add(ParagraphStyle(name='CodeR',fontName='Courier',fontSize=8.4,leading=12,spaceAfter=10))
styles.add(ParagraphStyle(name='CoverR',fontName='Helvetica-Bold',fontSize=32,leading=37,spaceAfter=18))
styles.add(ParagraphStyle(name='TitleR',fontName='Helvetica-Bold',fontSize=21,leading=26,spaceAfter=16))
styles.add(ParagraphStyle(name='SubR',fontName='Helvetica-Bold',fontSize=11.5,leading=15,spaceBefore=7,spaceAfter=7))
for n in styles.byName:styles[n].textColor=colors.black
class Figure(Flowable):
 def __init__(self,kind,height):Flowable.__init__(self);self.kind=kind;self.width=W;self.height=height
 def draw(self):
  c=self.canv; H=self.height
  def box(x,y,w,h,title,lines=(),shape='box'):
   c.setLineWidth(.7);c.setStrokeColor(colors.HexColor('#8779a4'));c.setFillColor(colors.HexColor('#f5f2fb'))
   if shape=='ellipse':c.ellipse(x,y,x+w,y+h,stroke=1,fill=1)
   else:c.roundRect(x,y,w,h,6,stroke=1,fill=1)
   c.setFillColor(colors.black);c.setFont('Helvetica-Bold',9)
   yy=y+h-15
   for line in title.split('\n'):c.drawCentredString(x+w/2,yy,line);yy-=11
   if lines:
    c.setStrokeColor(colors.HexColor('#d4ccdf'));c.line(x+5,yy+4,x+w-5,yy+4);yy-=9;c.setFont('Helvetica',8)
    for line in lines:c.drawString(x+8,yy,line);yy-=11
  def edge(x1,y1,x2,y2,label='',dash=False,head='normal'):
   c.setStrokeColor(colors.HexColor('#4a4553'));c.setFillColor(colors.HexColor('#4a4553'));c.setLineWidth(.65)
   if dash:c.setDash(3,2)
   c.line(x1,y1,x2,y2);c.setDash()
   a=math.atan2(y2-y1,x2-x1);s=6
   if head=='normal':
    p=c.beginPath();p.moveTo(x2,y2);p.lineTo(x2-s*math.cos(a-.4),y2-s*math.sin(a-.4));p.lineTo(x2-s*math.cos(a+.4),y2-s*math.sin(a+.4));p.close();c.drawPath(p,stroke=1,fill=1)
   elif head=='triangle':
    c.setFillColor(colors.white);p=c.beginPath();p.moveTo(x2,y2);p.lineTo(x2-9*math.cos(a-.4),y2-9*math.sin(a-.4));p.lineTo(x2-9*math.cos(a+.4),y2-9*math.sin(a+.4));p.close();c.drawPath(p,stroke=1,fill=1)
   if label:c.setFillColor(colors.black);c.setFont('Helvetica',7.3);c.drawCentredString((x1+x2)/2,(y1+y2)/2+5,label)
  def diamond(x,y):
   c.setFillColor(colors.HexColor('#4a4553'));p=c.beginPath();p.moveTo(x,y);p.lineTo(x+4,y-6);p.lineTo(x,y-12);p.lineTo(x-4,y-6);p.close();c.drawPath(p,fill=1,stroke=1)
  k=self.kind
  if k=='architecture':
   box(135,345,215,43,'Browser','',);box(135,270,215,48,'Spring Security',['Session authentication and CSRF']);box(135,193,215,48,'MVC and application services',['Validated commands and role checks'])
   box(0,92,145,57,'Domain rules',['Priority and lifecycle']);box(170,92,145,57,'Persistence boundary',['JPA JDBC transactions']);box(340,92,145,57,'Optional AI',['Advisor and transport'])
   box(170,7,145,47,'PostgreSQL',['Flyway schema and constraints'])
   edge(242,345,242,318);edge(242,270,242,241);edge(200,193,72,149);edge(242,193,242,149);edge(285,193,412,149);edge(242,92,242,54)
  elif k=='usecase':
   c.setStrokeColor(colors.HexColor('#bcb4c9'));c.rect(125,8,360,356);c.setFont('Helvetica-Bold',10);c.drawCentredString(305,348,'BITSOps Assistant')
   actors=[('Campus\nCommunity',310),('Technician',185),('Admin',65)]
   for title,y in actors:
    c.setFillColor(colors.black);c.setStrokeColor(colors.black);c.circle(45,y+12,7);c.line(45,y+5,45,y-16);c.line(27,y-2,63,y-2);c.line(45,y-16,30,y-30);c.line(45,y-16,60,y-30);c.setFont('Helvetica',9)
    for j,line in enumerate(title.split('\n')):c.drawCentredString(45,y-44-j*11,line)
   labels=[('Authenticate and view dashboard',305),('Report and track own incidents',257),('Reply review close and give feedback',209),('Investigate and resolve assigned work',161),('Request and review AI suggestions',113),('Manage catalog staff and policies',65),('Monitor history and reports',17)]
   for title,y in labels:box(155,y,300,33,title,shape='ellipse')
   for yy in [321,273,225]:edge(65,307,155,yy,head='none')
   for yy in [321,177,129]:edge(65,182,155,yy,head='none')
   for yy in [321,129,81,33]:edge(65,62,155,yy,head='none')
  elif k=='state':
   names=['NEW','CLASSIFIED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS','RESOLVED','VERIFIED','CLOSED']
   pos=[(20,300),(185,300),(350,300),(350,200),(185,200),(20,200),(20,100),(185,100)]
   for name,(x,y) in zip(names,pos):box(x,y,115,40,name)
   for ia,ib in zip(range(7),range(1,8)):
    x,y=pos[ia];xx,yy=pos[ib]
    if y==yy:edge(x+115 if xx>x else x,y+20,xx if xx>x else xx+115,yy+20)
    else:edge(x+57,y,xx+57,yy+40)
   edge(77,200,242,300,'rejection to CLASSIFIED',True)
   c.setFont('Helvetica',9);c.drawString(20,45,'Additional guarded reclassification edges are described below.')
  elif k=='domain':
   box(140,200,205,170,'Incident',['id : Long    version : long','status : IncidentStatus','reporterId, assigneeId : Long','reporter, assignee : UserAccount','assessment : PriorityAssessment','transition(next, now)','assign(userId, now)','reject(now) / correct(...)'])
   box(0,25,145,115,'PriorityAssessment',['impact, urgency, criticality : int','score : int / priority : String','getScore() / getPriority()'])
   box(175,25,140,115,'UserAccount',['email / passwordHash','role : Role / active : boolean','changeActive(active)'])
   box(345,25,140,115,'IncidentState\ninterface',['checkTransition(next)','forStatus(status)'])
   diamond(175,200);edge(175,188,73,140,'composition 1',head='none');edge(242,200,245,140,'reporter 1 / assignee 0..1');edge(310,200,415,140,'delegates validation')
  elif k=='contracts':
   box(145,285,195,70,'AssignmentStrategy\ninterface',['key() / select(List<Candidate>)'])
   box(10,175,210,65,'SkillBasedAssignment',['select(candidates)']);box(265,175,210,65,'LoadBasedAssignment',['select(candidates)'])
   edge(110,240,205,285,dash=True,head='triangle');edge(365,240,280,285,dash=True,head='triangle')
   box(140,80,205,65,'BaseNotificationChannel\nabstract',['final deliver(...)','channel() / deliveryStatus()'])
   box(10,0,210,48,'InAppNotificationChannel');box(265,0,210,48,'SimulatedEmailChannel');edge(115,48,205,80,head='triangle');edge(370,48,280,80,head='triangle')
  elif k=='ercore':
   c.translate(0,50)
   box(0,290,145,60,'user_accounts',['PK id / UNIQUE email']);box(342,290,145,60,'categories',['PK id / name / active'])
   box(0,170,145,65,'technician_profiles',['PK id / UNIQUE FK user_id','available / capacity'])
   box(342,170,145,65,'technician_skills',['PK FK user_id + category_id','proficiency 1..3'])
   box(168,40,150,75,'incidents',['PK id / FK reporter assignee','FK category / FK service','version / state / priority'])
   box(0,40,145,60,'it_services',['PK id / criticality / active'])
   box(0,-50,145,55,'sla_policies',['PK id / FK service nullable'])
   edge(72,290,72,235,'1 to 0..1');edge(145,202,342,202,'1 to many');edge(414,290,414,235,'1 to many');edge(145,315,210,115,'reporter / assignee');edge(342,315,275,115,'1 to many');edge(145,70,168,70,'');edge(72,40,72,5,'0..1 service')
  elif k=='erhistory':
   box(153,350,180,45,'incidents',['PK id'])
   edge(243,350,243,59,head='none')
   nodes=[(0,270,'incident_messages',['PK id / FK incident author','FK response_to self']), (297,270,'resolution_cycles',['PK incident_id + cycle','started_at / deadline']), (0,190,'resolutions',['PK id / FK incident technician','UNIQUE incident + cycle']), (297,190,'feedback',['PK FK incident_id','FK reporter / rating 1..5']), (0,110,'sla_events',['PK id / FK incident','UNIQUE incident cycle kind']), (297,110,'ai_suggestions',['PK id / FK incident requester','FK category nullable']), (0,30,'notifications',['PK id / FK user incident','channel / delivery_status']), (297,30,'audit_entries',['PK id / FK actor incident','action / state / timestamp'])]
   for x,y,t,ls in nodes:
    box(x,y,190,58,t,ls)
    edge(243,y+29,190 if x==0 else 297,y+29)
  elif k=='ersupport':
   box(5,195,225,65,'user_accounts',['PK id','credential_revision : BIGINT default 0'])
   box(5,65,225,100,'password_reset_tokens',['PK token_hash : VARCHAR(64)','FK user_id -> user_accounts.id','expires_at : TIMESTAMPTZ','used : BOOLEAN default false'])
   edge(117,195,117,165,'1 to many')
   box(265,195,215,65,'password_reset_limits',['PK email_hash : VARCHAR(64)','requested_at : TIMESTAMPTZ'])
   box(265,110,215,65,'password_reset_gate',['PK id = 1','window_start / requests'])
   box(265,25,215,65,'assignment_configuration',['PK id = 1 / strategy'])
   box(5,-5,225,50,'demo_seed_markers',['PK name : VARCHAR(100)'])
  elif k=='aiseq':
   xs=[48,180,310,440];names=['Staff browser','Advisor','OpenRouter','Suggestion store']
   for x,n in zip(xs,names):box(x-45,330,92,34,n);edge(x,328,x,10,dash=True,head='none')
   for a,b,y,t in [(0,1,292,'Request with incident version'),(1,2,245,'Bounded optional request'),(2,1,200,'Untrusted provider answer'),(1,3,145,'Validate and recheck version'),(3,0,90,'Saved advice for review'),(0,1,40,'Explicit apply later')]:edge(xs[a],y,xs[b],y,t)
  else:raise ValueError(k)
class ReportDoc(SimpleDocTemplate):
 def afterFlowable(self,f):
  if isinstance(f,Paragraph) and f.style.name=='TitleR':
   key='p'+str(self.page);self.canv.bookmarkPage(key);self.canv.addOutlineEntry(re.sub('<[^>]+>','',f.getPlainText()),key,0,False)
def footer(c,d):
 c.setStrokeColor(colors.HexColor('#d9d4e1'));c.setLineWidth(.5);c.line(54,43,558,43)
 c.setFillColor(colors.HexColor('#55515b'));c.setFont('Helvetica',8);c.drawString(54,29,'BITSOps Assistant | Detailed review report | 21 November 2026');c.drawRightString(558,29,f'{d.page} / 45')
def build():
 pages=json.loads((ROOT/'content.json').read_text());story=[];md=[]
 for i,page in enumerate(pages,1):
  if i>1:story.append(PageBreak())
  story.append(Paragraph(html.escape(page['title']),styles['CoverR'] if i==1 else styles['TitleR']));md.append(f"# {i} {page['title']}\n")
  for block in page['blocks']:
   kind=block['type']
   if kind in ['p','h','small','caption','code']:
    st={'p':'BodyR','h':'SubR','small':'SmallR','caption':'CaptionR','code':'CodeR'}[kind];t=block['text'];story.append(Paragraph(t,styles[st]));md.append(('## ' if kind=='h' else '')+re.sub('<[^>]+>','',html.unescape(t))+'\n')
   elif kind=='space':story.append(Spacer(1,block['height']))
   elif kind=='diagram':story.append(Figure(block['name'],block['height']));story.append(Spacer(1,12));md.append('[Diagram '+block['name']+']\n')
   elif kind=='table':
    data=[[Paragraph(html.escape(str(v)),styles['SmallR']) for v in row] for row in [block['headers']]+block['rows']];widths=block.get('widths',[W/len(data[0])]*len(data[0]));t=Table(data,colWidths=widths,repeatRows=1,hAlign='LEFT');t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#e5e0ef')),('GRID',(0,0),(-1,-1),.4,colors.HexColor('#d9d9d9')),('VALIGN',(0,0),(-1,-1),'MIDDLE'),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6),('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,colors.HexColor('#faf9fc')])]))
    story.extend([t,Spacer(1,10)]);md.extend(['| '+' | '.join(block['headers'])+' |','| '+' | '.join(['---']*len(data[0]))+' |']+['| '+' | '.join(map(str,r))+' |' for r in block['rows']]+[''])
 doc=ReportDoc(str(OUT),pagesize=(612,792),leftMargin=60,rightMargin=65,topMargin=52,bottomMargin=59,title='BITSOps Assistant Detailed Project Report',author='Group details pending')
 doc.build(story,onFirstPage=footer,onLaterPages=footer)
 (ROOT/'report.md').write_text('\n'.join(md))
 print(OUT)
if __name__=='__main__':build()
