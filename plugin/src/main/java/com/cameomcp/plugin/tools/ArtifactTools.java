package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.generictable.GenericTableManager;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.dependencymatrix.configuration.MatrixDataHelper;
import com.nomagic.magicdraw.dependencymatrix.datamodel.MatrixData;
import com.nomagic.magicdraw.dependencymatrix.datamodel.cell.AbstractMatrixCell;
import com.nomagic.magicdraw.dependencymatrix.datamodel.cell.DependencyEntry;
import com.nomagic.magicdraw.dependencymatrix.persistence.MatrixSettings;
import com.nomagic.magicdraw.dependencymatrix.persistence.PersistenceManager;
import com.nomagic.magicdraw.fileattachments.FileAttachmentsHelper;
import com.nomagic.magicdraw.hyperlinks.Hyperlink;
import com.nomagic.magicdraw.hyperlinks.HyperlinkUtils;
import com.nomagic.magicdraw.magicreport.helper.TemplateHelper;
import com.nomagic.magicdraw.magicreport.ui.bean.TemplateBean;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.properties.Property;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Comment;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Namespace;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.cameomcp.plugin.tools.Tool.Mode.*;

final class ArtifactTools {

    private ArtifactTools() {}

    static void register(Map<String, Tool> m) {
        // ---------------------------------------------------------------- generic tables
        Tools.add(m, "cameo_create_table", WRITE, c -> {
            Project p = c.project();
            c.model(); // generic tables need a UML model
            Diagram t = GenericTableManager.createGenericTable(p, c.str("name", "Table"));
            if (c.has("ownerId")) ModelElementsManager.getInstance().moveElement(t, c.el("ownerId"));
            if (c.has("elementTypes")) GenericTableManager.setTableElementTypes(t, new ArrayList<>(types(p, c.strs("elementTypes"))));
            if (c.has("scopeIds")) GenericTableManager.setScope(t, c.els("scopeIds"));
            if (c.has("columnIds")) GenericTableManager.addColumnsById(t, c.strs("columnIds"));
            return Model.summary(t);
        });

        Tools.add(m, "cameo_get_table_contents", READ, c -> {
            Diagram t = table(c);
            GenericTableManager.refreshTable(t);
            GenericTableManager.waitForCellValuesLoad(t);
            List<String> cols = GenericTableManager.getVisibleColumnIds(t);
            ObjectNode o = Model.ref(t);
            ArrayNode colsJ = o.putArray("columns");
            for (String id : cols) colsJ.addObject().put("id", id).put("name", GenericTableManager.getColumnNameById(t, id));
            ArrayNode rows = o.putArray("rows");
            int limit = c.i("limit", 500);
            for (Element row : GenericTableManager.getRowElements(t)) {
                if (rows.size() >= limit) break;
                ObjectNode r = rows.addObject().put("elementId", row.getID());
                ObjectNode cells = r.putObject("cells");
                for (String id : cols) {
                    Property cell = GenericTableManager.getCellValue(t, row, id);
                    if (cell != null) cells.put(GenericTableManager.getColumnNameById(t, id), cell.getValueStringRepresentation());
                }
            }
            return o;
        });

        Tools.add(m, "cameo_list_table_columns", READ, c -> {
            Element type = types(c.project(), List.of(c.str("elementType"))).get(0);
            ArrayNode a = Catalog.JSON.createArrayNode();
            GenericTableManager.getPossibleColumnIDs(type).forEach(a::add);
            return Catalog.JSON.createObjectNode().set("columnIds", a);
        });

        Tools.add(m, "cameo_add_table_columns", WRITE, c -> {
            Diagram t = table(c);
            GenericTableManager.addColumnsById(t, c.strs("columnIds"));
            ArrayNode a = Catalog.JSON.createArrayNode();
            GenericTableManager.getColumnIds(t).forEach(a::add);
            return Catalog.JSON.createObjectNode().set("columnIds", a);
        });

        Tools.add(m, "cameo_add_table_rows", WRITE, c -> {
            Diagram t = table(c);
            for (Element e : c.els("elementIds")) GenericTableManager.addRowElement(t, e);
            return Catalog.JSON.createObjectNode().put("rows", GenericTableManager.getRowElements(t).size());
        });

        Tools.add(m, "cameo_remove_table_rows", WRITE, c -> {
            Diagram t = table(c);
            for (Element e : c.els("elementIds")) GenericTableManager.removeRowElement(t, e);
            return Catalog.JSON.createObjectNode().put("rows", GenericTableManager.getRowElements(t).size());
        });

        Tools.add(m, "cameo_export_table_csv", READ, c -> {
            Diagram t = table(c);
            GenericTableManager.refreshTable(t);
            GenericTableManager.waitForCellValuesLoad(t);
            File f = new File(c.str("filePath"));
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            List<String> cols = GenericTableManager.getVisibleColumnIds(t);
            int n = 0;
            try (PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8)) {
                List<String> head = new ArrayList<>();
                for (String id : cols) head.add(csv(GenericTableManager.getColumnNameById(t, id)));
                w.println(String.join(",", head));
                for (Element row : GenericTableManager.getRowElements(t)) {
                    List<String> line = new ArrayList<>();
                    for (String id : cols) {
                        Property cell = GenericTableManager.getCellValue(t, row, id);
                        line.add(csv(cell == null ? "" : cell.getValueStringRepresentation()));
                    }
                    w.println(String.join(",", line));
                    n++;
                }
            }
            return Catalog.JSON.createObjectNode().put("file", f.getAbsolutePath()).put("rows", n);
        });

        // ---------------------------------------------------------------- dependency matrices
        Tools.add(m, "cameo_create_matrix", WRITE, c -> {
            Project p = c.project();
            Element owner = c.has("ownerId") ? c.el("ownerId") : c.model();
            if (!(owner instanceof Namespace ns)) throw new ToolError("Matrix owner must be a namespace");
            String type = c.str("type", "Dependency Matrix");
            Diagram d = ModelElementsManager.getInstance().createDiagram(type, ns);
            if (d == null) throw new ToolError("Cameo could not create a '" + type + "' (see cameo_list_diagram_types)");
            if (c.has("name")) d.setName(c.str("name"));
            PersistenceManager pm = new PersistenceManager(d);
            if (c.has("rowTypes")) pm.getRowSettings().setElementTypes(types(p, c.strs("rowTypes")));
            if (c.has("columnTypes")) pm.getColumnSettings().setElementTypes(types(p, c.strs("columnTypes")));
            if (c.has("rowScopeIds")) pm.getRowSettings().setScope(c.els("rowScopeIds"));
            if (c.has("columnScopeIds")) pm.getColumnSettings().setScope(c.els("columnScopeIds"));
            MatrixSettings ms = pm.getMatrixSettings();
            if (c.has("dependencyCriteria")) ms.setDependencyCriteria(c.strs("dependencyCriteria"));
            if (c.has("direction")) ms.setDirection(MatrixSettings.Direction.valueOf(c.str("direction").toUpperCase(Locale.ROOT)));
            return Model.summary(d).put("matrixType", type);
        });

        Tools.add(m, "cameo_get_matrix_contents", READ, c -> {
            Element e = c.el("matrixId");
            if (!(e instanceof Diagram d)) throw new ToolError("Not a matrix: " + e.getHumanType());
            MatrixData data = MatrixDataHelper.buildMatrix(d);
            if (data == null) throw new ToolError("Not a dependency matrix, or Cameo could not build it");
            ObjectNode o = Model.ref(d);
            o.put("rowCount", data.getRowElements().size()).put("columnCount", data.getColumnElements().size());
            ArrayNode cells = o.putArray("cells");
            int limit = c.i("limit", 1000);
            for (Element row : data.getRowElements()) {
                for (Element col : data.getColumnElements()) {
                    AbstractMatrixCell cell = data.getValue(row, col);
                    if (cell == null || AbstractMatrixCell.isStaticCell(cell) || cell.getDependencies().isEmpty()) continue;
                    if (cells.size() >= limit) { o.put("truncated", true); return o; }
                    ObjectNode x = cells.addObject();
                    x.set("row", Model.ref(row));
                    x.set("column", Model.ref(col));
                    ArrayNode deps = x.putArray("dependencies");
                    for (DependencyEntry de : cell.getDependencies()) {
                        ObjectNode dj = deps.addObject().put("type", de.getType()).put("name", de.getName())
                                .put("direction", String.valueOf(de.getDirection()));
                        ArrayNode cause = dj.putArray("causeIds");
                        de.getCause().forEach(ce -> cause.add(ce.getID()));
                    }
                }
            }
            return o;
        });

        // ---------------------------------------------------------------- reports
        Tools.add(m, "cameo_list_report_templates", READ, c -> {
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (TemplateBean t : TemplateHelper.listTemplates(c.b("reload", false))) {
                a.addObject().put("name", t.getName()).put("category", t.getCategory()).put("description", t.getDescription());
            }
            return Catalog.JSON.createObjectNode().set("templates", a);
        });

        // ---------------------------------------------------------------- attached files
        Tools.add(m, "cameo_attach_file", WRITE, c -> {
            Element owner = c.el("ownerId");
            File f = new File(c.str("filePath"));
            if (!f.isFile()) throw new ToolError("No such file: " + f);
            Comment att = FileAttachmentsHelper.createAttachedFileElement(owner);
            if (!FileAttachmentsHelper.storeFileToAttachedFile(att, f)) throw new ToolError("Cameo could not store " + f);
            return Model.ref(att);
        });

        Tools.add(m, "cameo_extract_attached_file", READ, c -> {
            Element att = c.el("attachedFileId");
            File dir = new File(c.str("directory"));
            dir.mkdirs();
            File out = FileAttachmentsHelper.extractToDirectory(dir, att);
            if (out == null) throw new ToolError("Element has no stored file");
            return Catalog.JSON.createObjectNode().put("file", out.getAbsolutePath()).put("bytes", out.length());
        });

        // ---------------------------------------------------------------- hyperlinks
        Tools.add(m, "cameo_add_hyperlink", WRITE, c -> {
            Element e = c.el("elementId");
            String text = c.str("text", "");
            Hyperlink h = c.has("targetElementId")
                    ? HyperlinkUtils.createHyperlink(text, c.el("targetElementId"))
                    : HyperlinkUtils.createHyperlink(text, c.str("url"), c.project());
            if (!HyperlinkUtils.addHyperlink(e, h)) throw new ToolError("Cameo refused the hyperlink");
            return links(e);
        });

        Tools.add(m, "cameo_list_hyperlinks", READ, c -> links(c.el("elementId")));

        Tools.add(m, "cameo_remove_hyperlink", WRITE, c -> {
            Element e = c.el("elementId");
            String url = c.str("url");
            Hyperlink hit = null;
            for (Hyperlink h : HyperlinkUtils.getAllHyperlinks(e)) if (url.equals(h.getUrl())) hit = h;
            if (hit == null) throw new ToolError("Element has no hyperlink " + url + " (see cameo_list_hyperlinks)");
            HyperlinkUtils.removeHyperlink(e, hit);
            return links(e);
        });
    }

    // ---------------------------------------------------------------- helpers

    private static Diagram table(Ctx c) {
        Element e = c.el("tableId");
        if (!(e instanceof Diagram d)) throw new ToolError("Not a table: " + e.getHumanType());
        return d;
    }

    /** Metaclass or stereotype elements for type names ("Class", "Block", "Requirement"...). */
    static List<Element> types(Project p, List<String> names) {
        List<Element> out = new ArrayList<>();
        for (String n : names) {
            Element meta = StereotypesHelper.getMetaClassByName(p, n);
            out.add(meta != null ? meta : Model.stereotype(p, n));
        }
        return out;
    }

    private static ObjectNode links(Element e) {
        ArrayNode a = Catalog.JSON.createArrayNode();
        Hyperlink active = HyperlinkUtils.getActiveHyperlink(e);
        for (Hyperlink h : HyperlinkUtils.getAllHyperlinks(e)) {
            a.addObject().put("url", h.getUrl()).put("text", h.getText()).put("active", h.equals(active));
        }
        ObjectNode o = Model.ref(e);
        o.set("hyperlinks", a);
        return o;
    }

    private static String csv(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
