package fosalgo.pheroswarm;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Runs the eight paired experiments using the unmodified PheroSwarm engine. */
public final class Tester {
    private static final int RADIUS = 4, MAX = 2500, STALL = 30;
    private static final double RHO = 0.05, DEPOSIT = 1.0;
    private static final boolean RECOVERY = true;
    private record Scenario(String map, String layout, Path file, int[][] grid, List<Point> starts,
                            String sha256) {}

    private Tester() {}

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of("") : Path.of(args[0]);
        Path out = root.resolve("result");
        List<Scenario> samples = new ArrayList<>();
        for (String map : List.of("A", "B")) {
            var c = load(root, map, "Clustered");
            var d = load(root, map, "Dispersed");
            if (!Arrays.deepEquals(c.grid(), d.grid()))
                throw new IOException("Clustered and dispersed grids differ for Map " + map);
            samples.add(c);
            samples.add(d);
        }
        Files.createDirectories(out);
        var runs = rows("Map", "Layout", "Mode", "Lambda", "Reachable cells", "Observed at step 0 (%)",
                "Final observed (%)", "Steps executed", "Step to 90%", "Step to 100%",
                "Visited (%)", "Revisits (%)", "Overlap (%)", "Moves", "Termination",
                "Frontier decisions", "Changed decisions", "Collision rejects", "Alternate moves");
        var curves = rows("Map", "Layout", "Mode", "Step", "Observed cells", "Observed (%)");
        var data = rows("Map", "Layout", "File", "Rows", "Columns", "Blocked cells", "Drone",
                "Start row (0-based)", "Start column (0-based)", "SHA-256");
        var settings = rows("Parameter", "Value");
        settings.add(new Object[]{"Sensor radius (cells)", RADIUS});
        settings.add(new Object[]{"Maximum steps", MAX});
        settings.add(new Object[]{"Stall limit", STALL});
        settings.add(new Object[]{"Evaporation", RHO});
        settings.add(new Object[]{"Deposit", DEPOSIT});
        settings.add(new Object[]{"Recovery", RECOVERY ? "Enabled" : "Disabled"});
        settings.add(new Object[]{"Modes", "M0: lambda=0; M1: lambda=1"});
        settings.add(new Object[]{"Coverage denominator", "Reachable free cells, from the engine mask"});
        settings.add(new Object[]{"Thresholds", "NR means not reached before termination"});
        settings.add(new Object[]{"Map provenance", "Map A = S01_G04; Map B = S01_G02"});
        StringBuilder log = new StringBuilder("PHEROSWARM | PAIRED EXPLORATION EXPERIMENT\n");
        for (int i = 1; i < settings.size(); i++)
            log.append(settings.get(i)[0]).append(" = ").append(settings.get(i)[1]).append('\n');
        for (Scenario s : samples) {
            int obstacles = 0;
            for (int[] row : s.grid()) for (int cell : row) if (cell == -1) obstacles++;
            for (int i = 0; i < s.starts().size(); i++) {
                Point p = s.starts().get(i);
                data.add(new Object[]{s.map(),s.layout(),s.file().getFileName().toString(),
                        s.grid().length,s.grid()[0].length,obstacles,i+1,p.row(),p.col(),s.sha256()});
            }
            Integer referenceReachable = null;
            for (double lambda : new double[]{0, 1}) {
                var agents = new ArrayList<Drone>();
                int[][] grid = Arrays.stream(s.grid()).map(int[]::clone).toArray(int[][]::new);
                for (Point p : s.starts()) agents.add(new Drone(grid, p.row(), p.col(), RADIUS, 10));
                var config = new ExplorationConfig(MAX, STALL, lambda, RHO, DEPOSIT, RECOVERY, "", 0);
                var result = new DecentralizedExploration(grid, agents, config);
                int[] histogram = new int[result.getSteps() + 1];
                var mask = result.getMustExploreMask();
                var first = result.getFirstObservedAt();
                int reachable = 0, total = 0;
                for (int r = 0; r < grid.length; r++) for (int c = 0; c < grid[r].length; c++) {
                    if (!mask[r][c]) continue;
                    reachable++;
                    if (first[r][c] >= 0) histogram[first[r][c]]++;
                }
                if (reachable == 0 || (referenceReachable != null && referenceReachable != reachable))
                    throw new IllegalStateException("Invalid paired coverage denominator for " + s.file());
                referenceReachable = reachable;
                int t90 = -1, t100 = -1;
                for (int t = 0; t < histogram.length; t++) {
                    total += histogram[t];
                    if (t90 < 0 && total * 10L >= reachable * 9L) t90 = t;
                    if (t100 < 0 && total == reachable) t100 = t;
                    curves.add(new Object[]{s.map(),s.layout(),config.mode(),t,total,100.0*total/reachable});
                }
                double initial = 100.0 * histogram[0] / reachable;
                if (Math.abs(100.0 * total / reachable - result.getObservedRatio()) > 1e-9
                        || ((t100 >= 0) != result.getTerminationReason().equals("ALL_REACHABLE_FREE_CELLS_OBSERVED")))
                    throw new IllegalStateException("Coverage or termination does not match engine output");
                runs.add(new Object[]{s.map(),s.layout(),config.mode(),lambda,reachable,initial,
                        result.getObservedRatio(),result.getSteps(),threshold(t90),threshold(t100),
                        result.getCoverage(),result.getRevisit(),result.getOverlap(),
                        result.getSuccessfulMoves(),result.getTerminationReason(),result.getDecisions(),
                        result.getChangedDecisions(),result.getCollisionRejects(),result.getAlternateMoves()});
                log.append(String.format(Locale.ROOT,
                        "%nMap %s / %s / %s | %s%nSHA-256: %s%nStarts (row,col; zero-based): %s%n"
                        + "Reachable: %d | Observed at step 0: %.6f%% | Final observed: %.6f%%%n"
                        + "Steps executed: %d | Step to 90%%: %s | Step to 100%%: %s%n"
                        + "Visited: %.6f%% | Revisit: %.6f%% | Overlap: %.6f%%%n"
                        + "Moves: %d | Frontier decisions: %d | Changed decisions: %d | "
                        + "Collision rejects: %d | Alternate moves: %d%nTermination: %s%n",
                        s.map(),s.layout(),config.mode(),s.file().getFileName(),s.sha256(),s.starts(),
                        reachable,initial,result.getObservedRatio(),result.getSteps(),threshold(t90),threshold(t100),
                        result.getCoverage(),result.getRevisit(),result.getOverlap(),result.getSuccessfulMoves(),
                        result.getDecisions(),result.getChangedDecisions(),result.getCollisionRejects(),
                        result.getAlternateMoves(),result.getTerminationReason()));
            }
        }
        writeWorkbook(out.resolve("PheroSwarm_Experiments.xlsx"), List.of("Runs","Coverage","Dataset","Protocol"),
                List.of(runs,curves,data,settings));
        Files.writeString(out.resolve("PheroSwarm_Experiments.txt"), log);
        System.out.println("Completed eight runs. Results: " + out.toAbsolutePath());
    }

    private static List<Object[]> rows(Object... headers) {
        List<Object[]> list = new ArrayList<>();
        list.add(headers);
        return list;
    }

    private static Object threshold(int t) { return t < 0 ? "NR" : t; }

    private static Scenario load(Path root, String map, String layout) throws Exception {
        Path f = root.resolve("dataset").resolve("PheroSwarm_Map" + map + "_" + layout + ".scw");
        try {
            byte[] bytes = Files.readAllBytes(f);
            List<String> lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
            if (!lines.get(0).startsWith("ENVIRONMENT")) throw new IOException("Missing environment header");
            int nr = header(lines.get(1), "NUM ROWS"), nc = header(lines.get(2), "NUM COLS");
            if (nr < 1 || nr > 200 || nc < 1 || nc > 400) throw new IOException("Grid outside supported dimensions");
            int[][] grid = new int[nr][nc];
            for (int r = 0; r < nr; r++) {
                String[] row = lines.get(r + 3).trim().split(":");
                if (row.length != nc) throw new IOException("Invalid row width at row " + r);
                for (int c = 0; c < nc; c++) {
                    int v = Integer.parseInt(row[c].trim());
                    if (v != 0 && v != -1) throw new IOException("Invalid occupancy value");
                    grid[r][c] = v;
                }
            }
            if (!lines.get(nr + 3).startsWith("DRONES") || header(lines.get(nr + 4), "NUM DRONES") != 4)
                throw new IOException("Expected four drones");
            var points = new ArrayList<Point>();
            for (int i = 0; i < 4; i++) {
                String[] pos = lines.get(nr + 5 + i).trim().split(":");
                int r = Integer.parseInt(pos[0].trim()), c = Integer.parseInt(pos[1].trim());
                Point p = new Point(r, c);
                if (r < 0 || c < 0 || r >= nr || c >= nc || grid[r][c] != 0 || points.contains(p))
                    throw new IOException("Invalid or duplicate start cell " + p);
                points.add(p);
            }
            if (lines.stream().skip(nr + 9L).anyMatch(s -> !s.isBlank()))
                throw new IOException("Unexpected data after start cells");
            return new Scenario(map, layout, f, grid, List.copyOf(points),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (IOException | RuntimeException ex) {
            throw new IOException("Cannot read " + f + ": " + ex.getMessage(), ex);
        }
    }

    private static int header(String line, String key) throws IOException {
        String[] fields = line.split(":", 2);
        if (fields.length != 2 || !fields[0].trim().equals(key)) throw new IOException("Expected " + key);
        return Integer.parseInt(fields[1].trim());
    }

    // Write a small standards-based XLSX package without adding a Maven dependency.
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PKG = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String CT = "application/vnd.openxmlformats-officedocument.spreadsheetml.";

    private static void writeWorkbook(Path output, List<String> names, List<List<Object[]>> sheets) throws IOException {
        try (var z = new ZipOutputStream(Files.newOutputStream(output))) {
            StringBuilder types = new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>");
            StringBuilder book = new StringBuilder("<workbook xmlns=\"" + NS + "\" xmlns:r=\"" + REL + "\"><sheets>");
            StringBuilder rels = new StringBuilder("<Relationships xmlns=\"" + PKG + "\">");
            for (int i = 1; i <= names.size(); i++) {
                book.append("<sheet name=\"").append(xml(names.get(i-1))).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
                rels.append(rel(i,"worksheet","worksheets/sheet"+i+".xml"));
                types.append(type("xl/worksheets/sheet"+i+".xml","worksheet"));
                entry(z,"xl/worksheets/sheet"+i+".xml",sheet(sheets.get(i-1)));
            }
            types.append(type("xl/workbook.xml","sheet.main")).append(type("xl/styles.xml","styles"));
            rels.append(rel(names.size()+1,"styles","styles.xml"));
            entry(z,"[Content_Types].xml",types+"</Types>");
            entry(z,"_rels/.rels","<Relationships xmlns=\"" + PKG + "\">"+rel(1,"officeDocument","xl/workbook.xml")+"</Relationships>");
            entry(z,"xl/workbook.xml",book+"</sheets></workbook>");
            entry(z,"xl/_rels/workbook.xml.rels",rels+"</Relationships>");
            entry(z,"xl/styles.xml",styles());
        }
    }

    private static String styles() {
        return """
                <styleSheet xmlns="%s">
                <fonts count="2"><font><sz val="11"/><name val="Calibri"/><color rgb="FF203448"/></font><font><b/><sz val="11"/><name val="Calibri"/><color rgb="FFFFFFFF"/></font></fonts>
                <fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF183D4B"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEAF3F3"/><bgColor indexed="64"/></patternFill></fill></fills>
                <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                <cellXfs count="5">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                <xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyFill="1"/>
                <xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                <xf numFmtId="2" fontId="0" fillId="3" borderId="0" xfId="0" applyNumberFormat="1" applyFill="1"/>
                </cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>
                """.formatted(NS);
    }

    private static String sheet(List<Object[]> rows) {
        int n = rows.get(0).length;
        String range = "A1:" + col(n-1) + rows.size();
        StringBuilder b = new StringBuilder("<worksheet xmlns=\"" + NS + "\"><dimension ref=\"" + range + "\"/><sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"16\"/><cols>");
        for (int i = 0; i < n; i++) {
            int width = 12;
            for (Object[] row : rows) if (row[i] instanceof String s) width = Math.max(width, s.length()+3);
            b.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1)
                    .append("\" width=\"").append(Math.min(72,width)).append("\" customWidth=\"1\"/>");
        }
        b.append("</cols><sheetData>");
        for (int i=0; i<rows.size(); i++) {
            b.append("<row r=\"").append(i+1).append(i==0 ? "\" ht=\"24\" customHeight=\"1\">" : "\">");
            Object[] row=rows.get(i);
            for (int j=0; j<n; j++) {
                Object v=row[j];
                int style=i==0 ? 1 : (v instanceof Double ? (i%2==0?4:3) : (i%2==0?2:0));
                b.append("<c r=\"").append(col(j)).append(i+1).append("\" s=\"").append(style);
                if (v instanceof Number) b.append("\"><v>").append(v).append("</v></c>");
                else b.append("\" t=\"inlineStr\"><is><t>").append(xml(String.valueOf(v))).append("</t></is></c>");
            }
            b.append("</row>");
        }
        return b.append("</sheetData><autoFilter ref=\"").append(range).append("\"/></worksheet>").toString();
    }

    private static String col(int i) {
        return (i>=26?col(i/26-1):"")+(char)('A'+i%26);
    }
    private static String xml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private static String type(String p,String suffix) {
        return "<Override PartName=\"/"+p+"\" ContentType=\""+CT+suffix+"+xml\"/>";
    }
    private static String rel(int id,String t,String p) {
        return "<Relationship Id=\"rId"+id+"\" Type=\""+REL+"/"+t+"\" Target=\""+p+"\"/>";
    }
    private static void entry(ZipOutputStream z,String p,String value) throws IOException {
        var e = new ZipEntry(p);
        e.setTime(0L);
        z.putNextEntry(e);
        z.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+value).getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
