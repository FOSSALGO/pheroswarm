package fosalgo.pheroswarm;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Builds compact, publication-oriented vector charts and editable tables from Tester output. */
public final class ChartAndTableGenerator {
    private static final String INK="#253746", BASE="#4d6075", M1="#087a72", GRID="#dce5e8", SOFT="#f2f7f7";
    private static final List<String> MAPS=List.of("A","B"), LAYOUTS=List.of("Clustered","Dispersed");
    private record Obs(int step,double pct) {}
    private ChartAndTableGenerator() {}

    public static void main(String[] args) throws Exception {
        Path root=args.length==0?Path.of(""):Path.of(args[0]);
        Path out=root.resolve("result");
        List<List<String>> runs, curves;
        try (var workbook=new ZipFile(out.resolve("PheroSwarm_Experiments.xlsx").toFile())) {
            runs=readSheet(workbook,1);
            curves=readSheet(workbook,2);
        }
        var byRun=new LinkedHashMap<String,List<String>>();
        var series=new LinkedHashMap<String,List<Obs>>();
        for(var r:runs) {
            if(byRun.put(key(r),r)!=null) throw new IOException("Duplicate run: "+key(r));
        }
        for(var r:curves) series.computeIfAbsent(key(r),k->new ArrayList<>())
                .add(new Obs(Integer.parseInt(r.get(3)),Double.parseDouble(r.get(5))));
        if(byRun.size()!=8 || !byRun.keySet().equals(series.keySet()))
            throw new IOException("Workbook does not contain eight matching runs and curves");
        for(String k:byRun.keySet()) {
            var values=series.get(k);
            values.sort(Comparator.comparingInt(Obs::step));
            var last=values.get(values.size()-1);
            for(int i=0;i<values.size();i++)
                if(values.get(i).step()!=i || (i>0 && values.get(i).pct()<values.get(i-1).pct()))
                    throw new IOException("Incomplete or nonmonotonic series: "+k);
            if(last.step()!=Integer.parseInt(byRun.get(k).get(7))
                    || Math.abs(last.pct()-Double.parseDouble(byRun.get(k).get(6)))>1e-8)
                throw new IOException("Final curve point does not match Runs: "+k);
        }
        writeCoverage(out,series);
        writeRedundancy(out,byRun);
        writeTables(out,byRun);
        System.out.println("Generated two vector charts and editable results tables in "+out.toAbsolutePath());
    }

    private static String key(List<String> r) {return String.join("/",r.subList(0,3));}

    // The workbook uses inline strings and numeric cells, as written by Tester.
    private static List<List<String>> readSheet(ZipFile z,int index) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setExpandEntityReferences(false);
        var entry=z.getEntry("xl/worksheets/sheet"+index+".xml");
        if(entry==null) throw new IOException("Missing worksheet "+index);
        try(var input=z.getInputStream(entry)) {
            NodeList rows=factory.newDocumentBuilder().parse(input).getElementsByTagName("row");
            List<List<String>> result=new ArrayList<>();
            for(int i=1;i<rows.getLength();i++) {
                List<String> values=new ArrayList<>();
                NodeList cells=((Element)rows.item(i)).getElementsByTagName("c");
                for(int j=0;j<cells.getLength();j++) values.add(cells.item(j).getTextContent().trim());
                result.add(values);
            }
            return result;
        }
    }

    private static void writeCoverage(Path out,Map<String,List<Obs>> all) throws IOException {
        StringBuilder b=svg(720,500,"Observed coverage by map and starting layout");
        int max=all.values().stream().mapToInt(v->v.get(v.size()-1).step()).max().orElseThrow();
        int interval=Math.max(1,(int)(Math.ceil(max/400.0)*100));
        int xMax=(int)Math.ceil(max/(double)interval)*interval;
        for(int i=0;i<4;i++) {
            String map=MAPS.get(i/2), layout=LAYOUTS.get(i%2);
            int x=75+344*(i%2), y=49+216*(i/2), w=273,h=152;
            label(b,x,y-14,"("+(char)('a'+i)+")  Map "+map+" · "+layout,15,"start",true);
            rect(b,x,y,w,h,SOFT);
            for(int p=0;p<=100;p+=25) {
                double yy=y+h-(h*p/100.0);
                line(b,x,yy,x+w,yy,GRID,0.8,"");
                label(b,x-9,yy+4,Integer.toString(p),12,"end",false);
            }
            for(int t=0;t<=xMax;t+=interval) {
                double xx=x+w*t/(double)xMax;
                line(b,xx,y+h,xx,y+h+4,INK,0.9,"");
                label(b,xx,y+h+17,Integer.toString(t),12,"middle",false);
            }
            line(b,x,y+h,x+w,y+h,INK,0.9,"");
            line(b,x,y,x,y+h,INK,0.9,"");
            for(String mode:List.of("M0","M1")) {
                var v=all.get(map+"/"+layout+"/"+mode);
                if(v==null) throw new IOException("Missing curve");
                String color=mode.equals("M0")?BASE:M1;
                StringBuilder d=new StringBuilder();
                for(Obs o:v) {
                    double xx=x+w*o.step()/((double)xMax), yy=y+h-h*o.pct()/100;
                    d.append(d.isEmpty()?"M":"H").append(n(xx));
                    if(d.indexOf("H")<0) d.append(' ').append(n(yy));
                    else d.append(" V").append(n(yy));
                }
                b.append("<path d=\"").append(d).append("\" fill=\"none\" stroke=\"").append(color)
                    .append("\" stroke-width=\"2.1\" ").append(mode.equals("M0")?"stroke-dasharray=\"5 3\"":"").append("/>");
                Obs last=v.get(v.size()-1);
                circle(b,x+w*last.step()/(double)xMax,y+h-h*last.pct()/100,2.8,color);
            }
        }
        b.append("<g transform=\"translate(23 235) rotate(-90)\">");
        label(b,0,0,"Observed coverage (%)",14,"middle",true);
        b.append("</g>");
        label(b,383,456,"Simulation step",14,"middle",true);
        legend(b,282,478);
        write(out,"PheroSwarm_Observed_Coverage.svg",b);
    }

    private static void writeRedundancy(Path out,Map<String,List<String>> runs) throws IOException {
        StringBuilder b=svg(720,274,"Repeat visits and inter-drone overlap");
        for(int k=0;k<2;k++) {
            int x=105+k*346,y=48,w=239,h=159;
            label(b,x,y-18,k==0?"(a) Revisit ratio":"(b) Inter-drone overlap",15,"start",true);
            rect(b,x,y,w,h,SOFT);
            for(int p=0;p<=100;p+=25) {
                double xx=x+w*p/100.0;
                line(b,xx,y,xx,y+h,GRID,0.8,"");
                label(b,xx,y+h+16,Integer.toString(p),12,"middle",false);
            }
            for(int i=0;i<4;i++) {
                String map=MAPS.get(i/2), layout=LAYOUTS.get(i%2);
                double yy=y+22+i*38;
                if(k==0) label(b,x-10,yy+4,map+" "+layout,12,"end",false);
                String key=map+"/"+layout+"/";
                double a=Double.parseDouble(runs.get(key+"M0").get(11+k));
                double c=Double.parseDouble(runs.get(key+"M1").get(11+k));
                double xa=x+w*a/100,xc=x+w*c/100;
                line(b,xa,yy,xc,yy,"#9daeb4",1.4,"");
                circle(b,xa,yy,4,BASE);
                rect(b,xc-4,yy-4,8,8,M1);
            }
            label(b,x+w/2,y+h+37,"Percent",13,"middle",true);
        }
        circle(b,293,260,4,BASE); label(b,303,264,"M0",12,"start",false);
        rect(b,362,256,8,8,M1); label(b,377,264,"M1",12,"start",false);
        write(out,"PheroSwarm_Redundancy.svg",b);
    }

    private static void writeTables(Path out,Map<String,List<String>> runs) throws IOException {
        String[] head={"Map","Start layout","Mode","Observed (%)","Visited (%)","Revisits (%)","Overlap (%)",
                "Steps to 90%","Steps to 100%","Termination"};
        int[] columns={0,1,2,6,10,11,12,8,9,14};
        StringBuilder csv=new StringBuilder(String.join(",",head)).append('\n');
        StringBuilder html=new StringBuilder("""
            <!doctype html><html lang="en"><meta charset="utf-8"><title>PheroSwarm paired runs</title>
            <style>body{font:14px Arial,sans-serif;color:#253746;margin:28px auto;max-width:970px}
            table{border-collapse:collapse;width:100%}caption{text-align:left;font-weight:bold;margin-bottom:10px}
            th,td{padding:7px 9px;text-align:right;border-bottom:1px solid #dce5e8;white-space:nowrap}
            th{background:#183d4b;color:white}th:nth-child(-n+3),td:nth-child(-n+3),th:last-child,td:last-child{text-align:left}
            tbody tr:nth-child(4n+3),tbody tr:nth-child(4n+4){background:#f2f7f7}
            p{font-size:12px;color:#445563}
            @media print{body{margin:0}th{color:#253746;background:white;border-top:2px solid #253746}}
            </style><table><caption>Paired exploration outcomes</caption><thead><tr>
            """);
        for(String h:head) html.append("<th>").append(h).append("</th>");
        html.append("</tr></thead><tbody>");
        for(var row:runs.values()) {
            html.append("<tr>");
            List<String> items=new ArrayList<>();
            for(int i:columns) {
                String v=row.get(i);
                if(i==6 || (i>=10 && i<=12)) v=n(Double.parseDouble(v));
                if(i==14 && v.equals("ALL_REACHABLE_FREE_CELLS_OBSERVED")) v="Full coverage";
                html.append("<td>").append(esc(v)).append("</td>");
                items.add(v);
            }
            csv.append(String.join(",",items)).append('\n');
            html.append("</tr>");
        }
        html.append("</tbody></table><p>NR = threshold not reached. Observed and visited coverage use reachable free cells as the denominator.</p></html>");
        Files.writeString(out.resolve("PheroSwarm_Paired_Outcomes.csv"),csv);
        Files.writeString(out.resolve("PheroSwarm_Paired_Outcomes.html"),html);
    }

    private static StringBuilder svg(int w,int h,String title) {
        return new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"180mm\" height=\"")
                .append(n(180.0*h/w)).append("mm\" viewBox=\"0 0 ").append(w).append(' ').append(h)
                .append("\" role=\"img\"><title>").append(esc(title)).append("</title><rect width=\"100%\" height=\"100%\" fill=\"white\"/>");
    }
    private static void write(Path out,String name,StringBuilder b)throws IOException {
        Files.writeString(out.resolve(name),b.append("</svg>").toString());
    }
    private static void legend(StringBuilder b,int x,int y) {
        line(b,x,y,x+26,y,BASE,2,"5 3"); label(b,x+33,y+4,"M0 (λ=0)",12,"start",false);
        line(b,x+125,y,x+151,y,M1,2,""); label(b,x+158,y+4,"M1 (λ=1)",12,"start",false);
    }
    private static void rect(StringBuilder b,double x,double y,double w,double h,String fill) {
        b.append("<rect x=\"").append(n(x)).append("\" y=\"").append(n(y)).append("\" width=\"").append(n(w))
                .append("\" height=\"").append(n(h)).append("\" fill=\"").append(fill).append("\"/>");
    }
    private static void circle(StringBuilder b,double x,double y,double r,String fill) {
        b.append("<circle cx=\"").append(n(x)).append("\" cy=\"").append(n(y)).append("\" r=\"").append(n(r))
                .append("\" fill=\"").append(fill).append("\"/>");
    }
    private static void line(StringBuilder b,double x,double y,double x2,double y2,String color,double width,String dash) {
        b.append("<line x1=\"").append(n(x)).append("\" y1=\"").append(n(y)).append("\" x2=\"").append(n(x2))
                .append("\" y2=\"").append(n(y2)).append("\" stroke=\"").append(color).append("\" stroke-width=\"")
                .append(n(width)).append("\"").append(dash.isEmpty()?"":" stroke-dasharray=\""+dash+"\"").append("/>");
    }
    private static void label(StringBuilder b,double x,double y,String text,int size,String anchor,boolean bold) {
        b.append("<text x=\"").append(n(x)).append("\" y=\"").append(n(y)).append("\" font-family=\"Arial, DejaVu Sans, sans-serif\" fill=\"").append(INK)
                .append("\" font-size=\"").append(size).append("\" text-anchor=\"").append(anchor)
                .append("\" font-weight=\"").append(bold?"700":"400").append("\">").append(esc(text)).append("</text>");
    }
    private static String n(double x) {return String.format(Locale.ROOT,"%.2f",x);}
    private static String esc(String s) {return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
