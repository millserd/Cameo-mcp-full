package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.copypaste.CopyPasting;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.export.image.ImageExporter;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager;
import com.nomagic.magicdraw.properties.Property;
import com.nomagic.magicdraw.properties.PropertyManager;
import com.nomagic.magicdraw.ui.diagrams.CustomizableDiagramsManager;
import com.nomagic.magicdraw.uml.DiagramTypeConstants;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.magicdraw.uml.symbols.PropertyBasedPresentationElement;
import com.nomagic.magicdraw.uml.symbols.layout.Layouting;
import com.nomagic.magicdraw.uml.symbols.paths.PathElement;
import com.nomagic.magicdraw.uml.symbols.shapes.ShapeElement;
import com.nomagic.uml2.ext.jmi.helpers.CoreHelper;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.ActivityPartition;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Namespace;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import static com.cameomcp.plugin.tools.Tool.Mode.*;

final class DiagramTools {

    private DiagramTools() {}

    /** Short names agents use -> Cameo diagram type names. */
    static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
            Map.entry("bdd", "SysML Block Definition Diagram"), Map.entry("ibd", "SysML Internal Block Diagram"),
            Map.entry("act", "SysML Activity Diagram"), Map.entry("stm", "SysML State Machine Diagram"),
            Map.entry("sd", "SysML Sequence Diagram"), Map.entry("uc", "SysML Use Case Diagram"),
            Map.entry("req", "SysML Requirement Diagram"), Map.entry("pkg", "SysML Package Diagram"),
            Map.entry("par", "SysML Parametric Diagram"));

    static void register(Map<String, Tool> m) {
        Tools.add(m, "cameo_list_diagram_types", READ, c -> {
            TreeMap<String, String> types = new TreeMap<>();
            DiagramTypeConstants.STANDARD_CREATABLE_TYPES.forEach(t -> types.put(t, "UML"));
            CustomizableDiagramsManager.getInstance().getDiagrams().forEach(d -> {
                if (d.isCreatable()) types.put(d.getDiagramTypeId(), d.getCategory());
            });
            ArrayNode a = Catalog.JSON.createArrayNode();
            types.forEach((t, cat) -> a.addObject().put("type", t).put("category", cat));
            ObjectNode o = Catalog.JSON.createObjectNode();
            o.set("types", a);
            o.set("aliases", Catalog.JSON.valueToTree(TYPE_ALIASES));
            return o;
        });

        Tools.add(m, "cameo_list_diagrams", READ, c -> {
            Project p = c.project();
            String type = c.opt("type");
            Element scope = c.elOpt("ownerId");
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (DiagramPresentationElement d : p.getDiagrams()) {
                Diagram dg = d.getDiagram();
                if (type != null && !matchesType(d, type)) continue;
                if (scope != null && !isInside(dg, scope)) continue;
                a.add(Model.summary(dg).put("diagramType", d.getDiagramType().getType()));
            }
            return Catalog.JSON.createObjectNode().set("diagrams", a);
        });

        Tools.add(m, "cameo_create_diagram", WRITE, c -> {
            Project p = c.project();
            String type = diagramType(c.str("type"));
            Element owner = c.has("ownerId") ? c.el("ownerId") : c.model();
            if (!(owner instanceof Namespace ns)) throw new ToolError("A diagram owner must be a namespace (package, block, activity...), not " + owner.getHumanType());
            Diagram d = ModelElementsManager.getInstance().createDiagram(type, ns);
            if (d == null) throw new ToolError("Cameo could not create a '" + type + "' here (see cameo_list_diagram_types)");
            if (c.has("name")) d.setName(c.str("name"));
            return Model.summary(d).put("diagramType", type);
        });

        Tools.add(m, "cameo_open_diagram", APP, c -> {
            DiagramPresentationElement d = dpe(c);
            d.open();
            return Model.ref(d.getDiagram()).put("opened", true);
        });

        Tools.add(m, "cameo_close_diagram", APP, c -> {
            DiagramPresentationElement d = dpe(c);
            d.close();
            return Model.ref(d.getDiagram()).put("closed", true);
        });

        Tools.add(m, "cameo_get_diagram_contents", READ, c -> {
            DiagramPresentationElement d = dpe(c);
            ObjectNode o = Model.summary(d.getDiagram()).put("diagramType", d.getDiagramType().getType());
            ArrayNode shapes = o.putArray("shapes");
            ArrayNode paths = o.putArray("paths");
            forEachPe(d, pe -> (pe instanceof PathElement ? paths : shapes).add(peJson(pe)));
            return o;
        });

        Tools.add(m, "cameo_add_to_diagram", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            ArrayNode out = Catalog.JSON.createArrayNode();
            for (JsonNode item : items(c, "items", "elementId")) {
                Element e = c.find(item.path("elementId").asText());
                PresentationElement parent = item.has("parentPresentationId") ? pe(d, item.get("parentPresentationId").asText()) : d;
                Point at = new Point(item.path("x").asInt(50), item.path("y").asInt(50));
                PresentationElement created;
                if (item.has("nestedPath")) {
                    List<com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property> path = new ArrayList<>();
                    for (JsonNode n : item.get("nestedPath")) path.add((com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property) c.find(n.asText()));
                    created = PresentationElementsManager.getInstance().createPartShape(
                            (com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property) e, parent, path, item.path("createRelationships").asBoolean(false), at);
                } else {
                    created = PresentationElementsManager.getInstance().createShapeElement(e, parent,
                            item.path("createRelationships").asBoolean(false), at);
                }
                if (created == null) throw new ToolError("Cameo cannot show a " + e.getHumanType() + " on this diagram/parent");
                out.add(peJson(created));
            }
            return Catalog.JSON.createObjectNode().set("added", out);
        });

        Tools.add(m, "cameo_add_path_to_diagram", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            ArrayNode out = Catalog.JSON.createArrayNode();
            for (JsonNode item : items(c, "paths", "relationshipId")) {
                Element r = c.find(item.path("relationshipId").asText());
                PresentationElement client = item.has("sourcePresentationId") ? pe(d, item.get("sourcePresentationId").asText())
                        : shapeOf(d, CoreHelper.getClientElement(r));
                PresentationElement supplier = item.has("targetPresentationId") ? pe(d, item.get("targetPresentationId").asText())
                        : shapeOf(d, CoreHelper.getSupplierElement(r));
                PresentationElement path = client == supplier
                        ? PresentationElementsManager.getInstance().createPathToSelfShapeElement(r, client)
                        : PresentationElementsManager.getInstance().createPathElement(r, client, supplier);
                if (path == null) throw new ToolError("Cameo cannot draw this " + r.getHumanType() + " between these symbols");
                out.add(peJson(path));
            }
            return Catalog.JSON.createObjectNode().set("added", out);
        });

        Tools.add(m, "cameo_delete_shapes", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            int n = 0;
            for (String id : c.strs("presentationIds")) {
                PresentationElementsManager.getInstance().deletePresentationElement(pe(d, id));
                n++;
            }
            return Catalog.JSON.createObjectNode().put("deleted", n);
        });

        Tools.add(m, "cameo_move_shapes", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            ArrayNode out = Catalog.JSON.createArrayNode();
            for (JsonNode mv : c.node("moves")) {
                PresentationElement pe = pe(d, mv.path("presentationId").asText());
                if (!(pe instanceof ShapeElement s)) throw new ToolError(pe.getHumanType() + " is not a shape; use cameo_route_paths for paths");
                Rectangle b = s.getBounds();
                Rectangle nb = new Rectangle(mv.path("x").asInt(b.x), mv.path("y").asInt(b.y),
                        mv.path("width").asInt(b.width), mv.path("height").asInt(b.height));
                PresentationElementsManager.getInstance().reshapeShapeElement(s, nb);
                out.add(peJson(s));
            }
            return Catalog.JSON.createObjectNode().set("moved", out);
        });

        Tools.add(m, "cameo_reparent_shapes", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            PresentationElement parent = pe(d, c.str("parentPresentationId"));
            List<PresentationElement> views = new ArrayList<>();
            for (String id : c.strs("presentationIds")) views.add(pe(d, id));
            Point at = new Point(c.i("x", parent.getBounds().x + 20), c.i("y", parent.getBounds().y + 40));
            PresentationElementsManager.getInstance().movePresentationElements(views, parent, at, c.b("changeElementOwner", false));
            ArrayNode out = Catalog.JSON.createArrayNode();
            views.forEach(v -> out.add(peJson(v)));
            return Catalog.JSON.createObjectNode().set("moved", out);
        });

        Tools.add(m, "cameo_align_symbols", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            List<ShapeElement> shapes = new ArrayList<>();
            for (String id : c.strs("presentationIds")) {
                if (pe(d, id) instanceof ShapeElement s) shapes.add(s);
                else throw new ToolError(id + " is not a shape");
            }
            if (shapes.size() < 2) throw new ToolError("Give at least two shapes");
            String how = c.str("alignment").toLowerCase(Locale.ROOT);
            Rectangle first = shapes.get(0).getBounds();
            int gap = c.i("spacing", 40);
            int cursor = how.startsWith("distribute-h") ? first.x : first.y;
            for (ShapeElement s : shapes) {
                Rectangle b = new Rectangle(s.getBounds());
                switch (how) {
                    case "left" -> b.x = first.x;
                    case "right" -> b.x = first.x + first.width - b.width;
                    case "top" -> b.y = first.y;
                    case "bottom" -> b.y = first.y + first.height - b.height;
                    case "center" -> b.x = first.x + first.width / 2 - b.width / 2;
                    case "middle" -> b.y = first.y + first.height / 2 - b.height / 2;
                    case "distribute-horizontal" -> { b.x = cursor; b.y = first.y; cursor += b.width + gap; }
                    case "distribute-vertical" -> { b.y = cursor; b.x = first.x; cursor += b.height + gap; }
                    default -> throw new ToolError("alignment: left, right, top, bottom, center, middle, distribute-horizontal, distribute-vertical");
                }
                PresentationElementsManager.getInstance().reshapeShapeElement(s, b);
            }
            return Catalog.JSON.createObjectNode().put("aligned", shapes.size());
        });

        Tools.add(m, "cameo_layout_diagram", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            String layouter = switch (c.str("style", "default").toLowerCase(Locale.ROOT)) {
                case "default" -> null;
                case "hierarchic" -> Layouting.HIERARCHIC_DIAGRAM_LAYOUTER;
                case "orthogonal" -> Layouting.ORTHOGONAL_DIAGRAM_LAYOUTER;
                case "tree" -> Layouting.TREE_DIAGRAM_LAYOUTER;
                case "organic" -> Layouting.ORGANIC_DIAGRAM_LAYOUTER;
                case "circular" -> Layouting.CIRCULAR_DIAGRAM_LAYOUTER;
                case "grid" -> Layouting.GRID_DIAGRAM_LAYOUTER;
                case "class" -> Layouting.CLASS_DIAGRAM_LAYOUTER;
                case "activity" -> Layouting.ACTIVITY_DIAGRAM_LAYOUTER;
                case "state" -> Layouting.STATE_DIAGRAM_LAYOUTER;
                case "composite" -> Layouting.COMPOSITE_DIAGRAM_LAYOUTER;
                default -> throw new ToolError("style: default, hierarchic, orthogonal, tree, organic, circular, grid, class, activity, state, composite");
            };
            boolean ok = layouter == null ? Layouting.layout(d) : Layouting.layout(d, layouter);
            if (!ok) throw new ToolError("Cameo could not lay out this diagram with that style");
            return Catalog.JSON.createObjectNode().put("laidOut", true);
        });

        Tools.add(m, "cameo_route_paths", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            ArrayNode out = Catalog.JSON.createArrayNode();
            for (JsonNode r : c.node("routes")) {
                if (!(pe(d, r.path("presentationId").asText()) instanceof PathElement path)) throw new ToolError("Not a path: " + r.path("presentationId").asText());
                List<Point> pts = new ArrayList<>();
                for (JsonNode pt : r.path("breakpoints")) pts.add(new Point(pt.path("x").asInt(), pt.path("y").asInt()));
                PresentationElementsManager.getInstance().changePathBreakPoints(path, pts);
                if (r.path("resetLabels").asBoolean(false)) PresentationElementsManager.getInstance().resetLabelPositions(path);
                out.add(peJson(path));
            }
            return Catalog.JSON.createObjectNode().set("routed", out);
        });

        Tools.add(m, "cameo_reset_label_positions", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            Set<String> only = new HashSet<>(c.strs("presentationIds"));
            List<PathElement> paths = new ArrayList<>();
            forEachPe(d, pe -> {
                if (pe instanceof PathElement path && (only.isEmpty() || only.contains(pe.getID()))) paths.add(path);
            });
            for (PathElement path : paths) PresentationElementsManager.getInstance().resetLabelPositions(path);
            return Catalog.JSON.createObjectNode().put("paths", paths.size());
        });

        Tools.add(m, "cameo_get_shape_properties", READ, c -> {
            PresentationElement pe = pe(dpe(c), c.str("presentationId"));
            ObjectNode o = peJson(pe);
            ArrayNode a = o.putArray("properties");
            for (Property prop : props(pe).getProperties()) {
                a.addObject().put("id", prop.getID()).put("name", prop.getName()).put("value", prop.getValueStringRepresentation());
            }
            return o;
        });

        Tools.add(m, "cameo_set_shape_properties", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            ArrayNode out = Catalog.JSON.createArrayNode();
            for (String id : c.strs("presentationIds")) {
                PresentationElement pe = pe(d, id);
                PropertyManager changes = new PropertyManager();
                Iterator<Map.Entry<String, JsonNode>> it = c.node("properties").fields();
                while (it.hasNext()) {
                    var en = it.next();
                    Property cur = props(pe).getProperty(en.getKey());
                    if (cur == null) cur = props(pe).getPropertyByName(en.getKey());
                    if (cur == null) throw new ToolError(pe.getHumanType() + " has no display property '" + en.getKey() + "' (see cameo_get_shape_properties)");
                    Property p = (Property) cur.clone();
                    p.setValue(displayValue(cur.getValue(), en.getValue()));
                    changes.addProperty(p);
                }
                PresentationElementsManager.getInstance().setPresentationElementProperties(pe, changes);
                out.add(peJson(pe));
            }
            return Catalog.JSON.createObjectNode().set("updated", out);
        });

        Tools.add(m, "cameo_copy_symbol_style", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            PresentationElement src = pe(d, c.str("sourcePresentationId"));
            List<PresentationElement> targets = new ArrayList<>();
            for (String id : c.strs("targetPresentationIds")) targets.add(pe(d, id));
            CopyPasting.copyPasteSymbolsStyle(src, targets);
            return Catalog.JSON.createObjectNode().put("styled", targets.size());
        });

        Tools.add(m, "cameo_create_note", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            PresentationElementsManager pem = PresentationElementsManager.getInstance();
            Point at = new Point(c.i("x", 50), c.i("y", 50));
            ShapeElement s = switch (c.str("kind", "note").toLowerCase(Locale.ROOT)) {
                case "note" -> pem.createNote(d, at);
                case "textbox" -> pem.createTextBox(d, at);
                case "rectangle" -> pem.createRectangularShape(d, at);
                case "separator" -> pem.createSeparator(d, at);
                default -> throw new ToolError("kind: note, textbox, rectangle, separator");
            };
            if (c.has("text")) pem.setText(s, c.str("text"));
            for (String id : c.strs("anchorTo")) pem.connectNote(s, pe(d, id));
            return peJson(s);
        });

        Tools.add(m, "cameo_create_swimlanes", WRITE, c -> {
            Project p = c.project();
            DiagramPresentationElement d = dpe(c);
            Element activity = d.getDiagram().getOwner();
            List<ActivityPartition> parts = new ArrayList<>();
            for (String n : c.strs("partitions")) {
                Element existing = p.getElementByID(n) instanceof Element e ? e : null;
                if (existing instanceof ActivityPartition ap) { parts.add(ap); continue; }
                ActivityPartition ap = p.getElementsFactory().createActivityPartitionInstance();
                ap.setName(n);
                ModelElementsManager.getInstance().addElement(ap, activity);
                parts.add(ap);
            }
            boolean vertical = c.b("vertical", true);
            PresentationElement sl = PresentationElementsManager.getInstance().createSwimlane(
                    vertical ? List.of() : parts, vertical ? parts : List.of(), d);
            ArrayNode ids = Catalog.JSON.createArrayNode();
            parts.forEach(ap -> ids.add(Model.ref(ap)));
            ObjectNode o = peJson(sl);
            o.set("partitions", ids);
            return o;
        });

        Tools.add(m, "cameo_select_diagram_symbols", APP, c -> {
            DiagramPresentationElement d = dpe(c);
            List<PresentationElement> sel = new ArrayList<>();
            for (String id : c.strs("presentationIds")) sel.add(pe(d, id));
            d.open();
            d.setSelected(sel);
            return Catalog.JSON.createObjectNode().put("selected", sel.size());
        });

        Tools.add(m, "cameo_set_diagram_grid", WRITE, c -> {
            DiagramPresentationElement d = dpe(c);
            if (c.has("show")) d.setShowGrid(c.b("show", true));
            if (c.has("size")) d.setGridSize(c.i("size", DiagramPresentationElement.DEFAULT_GRID_SIZE));
            if (c.has("snapShapes")) d.setSnapToGridShapes(c.b("snapShapes", true));
            if (c.has("snapPaths")) d.setSnapToGridPaths(c.b("snapPaths", true));
            return Catalog.JSON.createObjectNode().put("show", d.isShowGrid()).put("size", d.getGridSize())
                    .put("snapShapes", d.isSnapToGridShapes()).put("snapPaths", d.isSnapToGridPaths());
        });

        Tools.add(m, "cameo_export_diagram", READ, c -> {
            DiagramPresentationElement d = dpe(c);
            File f = new File(c.str("filePath"));
            String fmt = c.str("format", ext(f)).toLowerCase(Locale.ROOT);
            int code = switch (fmt) {
                case "png" -> ImageExporter.PNG;
                case "svg" -> ImageExporter.SVG;
                case "jpg", "jpeg" -> ImageExporter.JPEG;
                case "emf" -> ImageExporter.EMF;
                case "eps" -> ImageExporter.EPS;
                case "tif", "tiff" -> ImageExporter.TIFF;
                default -> throw new ToolError("format: png, svg, jpg, emf, eps, tiff");
            };
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            d.ensureLoaded();
            Rectangle r = ImageExporter.export(d, code, f, false, c.i("dpi", 96), c.i("scalePercent", 100));
            if (!f.isFile()) throw new ToolError("Export produced no file");
            return Catalog.JSON.createObjectNode().put("file", f.getAbsolutePath()).put("bytes", f.length())
                    .put("width", r == null ? 0 : r.width).put("height", r == null ? 0 : r.height);
        });

        Tools.add(m, "cameo_get_diagram_image", READ, c -> {
            DiagramPresentationElement d = dpe(c);
            d.ensureLoaded();
            BufferedImage img = ImageExporter.export(d, false, c.i("scalePercent", 100));
            if (img == null) throw new ToolError("Diagram is empty or could not be rendered");
            int maxW = c.i("maxWidth", 1600);
            int maxH = c.i("maxHeight", 1600);
            double k = Math.min(1.0, Math.min(maxW / (double) img.getWidth(), maxH / (double) img.getHeight()));
            if (k < 1.0) {
                int w = Math.max(1, (int) (img.getWidth() * k));
                int h = Math.max(1, (int) (img.getHeight() * k));
                BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = small.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, w, h, null);
                g.dispose();
                img = small;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            ObjectNode meta = Model.ref(d.getDiagram()).put("width", img.getWidth()).put("height", img.getHeight());
            return new Tools.Image(Base64.getEncoder().encodeToString(bos.toByteArray()), "image/png", meta);
        });
    }

    // ---------------------------------------------------------------- helpers

    static String diagramType(String t) {
        return TYPE_ALIASES.getOrDefault(t.toLowerCase(Locale.ROOT), t);
    }

    private static boolean matchesType(DiagramPresentationElement d, String type) {
        String want = diagramType(type);
        return d.getDiagramType().getType().equalsIgnoreCase(want);
    }

    private static boolean isInside(Element e, Element scope) {
        for (Element x = e; x != null; x = x.getOwner()) if (x == scope) return true;
        return false;
    }

    static DiagramPresentationElement dpe(Ctx c) {
        Element e = c.el("diagramId");
        if (!(e instanceof Diagram dg)) throw new ToolError("Not a diagram: " + e.getHumanType() + " " + e.getID());
        DiagramPresentationElement d = c.project().getDiagram(dg);
        d.ensureLoaded();
        return d;
    }

    static void forEachPe(PresentationElement root, Consumer<PresentationElement> visit) {
        for (PresentationElement pe : root.getPresentationElements()) {
            visit.accept(pe);
            forEachPe(pe, visit);
        }
    }

    static PresentationElement pe(DiagramPresentationElement d, String id) {
        PresentationElement[] hit = {null};
        forEachPe(d, pe -> { if (id.equals(pe.getID())) hit[0] = pe; });
        if (hit[0] == null) throw new ToolError("No symbol with presentationId '" + id + "' on this diagram (see cameo_get_diagram_contents)");
        return hit[0];
    }

    static PresentationElement shapeOf(DiagramPresentationElement d, Element e) {
        PresentationElement[] hit = {null};
        forEachPe(d, pe -> { if (hit[0] == null && pe instanceof ShapeElement && pe.getElement() == e) hit[0] = pe; });
        if (hit[0] == null) throw new ToolError("'" + Model.name(e) + "' is not shown on this diagram; add it first or pass the presentation ids");
        return hit[0];
    }

    static ObjectNode peJson(PresentationElement pe) {
        ObjectNode o = Catalog.JSON.createObjectNode().put("presentationId", pe.getID()).put("type", pe.getHumanType());
        Element e = pe.getElement();
        if (e != null) o.set("element", Model.ref(e));
        Rectangle b = pe.getBounds();
        if (b != null) o.putObject("bounds").put("x", b.x).put("y", b.y).put("width", b.width).put("height", b.height);
        if (pe.getParent() != null && !(pe.getParent() instanceof DiagramPresentationElement)) o.put("parentPresentationId", pe.getParent().getID());
        if (pe instanceof PathElement path) {
            if (path.getClient() != null) o.put("sourcePresentationId", path.getClient().getID());
            if (path.getSupplier() != null) o.put("targetPresentationId", path.getSupplier().getID());
        }
        return o;
    }

    /** Single item from top-level args, or an array of items. */
    private static Iterable<JsonNode> items(Ctx c, String arrayKey, String singleKey) {
        if (c.has(arrayKey)) return c.node(arrayKey);
        if (!c.has(singleKey)) throw new ToolError("Pass '" + arrayKey + "' (array) or '" + singleKey + "'");
        return List.of(c.args);
    }

    private static Object displayValue(Object current, JsonNode v) {
        if (current instanceof Boolean) return v.asBoolean();
        if (current instanceof Integer) return v.asInt();
        if (current instanceof Double || current instanceof Float) return v.asDouble();
        if (current instanceof Color) {
            try {
                return Color.decode(v.asText());
            } catch (NumberFormatException e) {
                throw new ToolError("Colors are hex strings like \"#FF8800\"");
            }
        }
        return v.asText();
    }

    private static PropertyManager props(PresentationElement pe) {
        if (pe instanceof PropertyBasedPresentationElement pb) return pb.getPropertyManager();
        throw new ToolError(pe.getHumanType() + " has no display properties");
    }

    private static String ext(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i < 0 ? "png" : n.substring(i + 1);
    }
}
