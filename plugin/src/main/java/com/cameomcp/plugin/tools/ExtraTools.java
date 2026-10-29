package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.magicreport.GenerateTask;
import com.nomagic.magicdraw.magicreport.helper.TemplateHelper;
import com.nomagic.magicdraw.magicreport.ui.bean.PackageSelectionBean;
import com.nomagic.magicdraw.magicreport.ui.bean.ReportBean;
import com.nomagic.magicdraw.magicreport.ui.bean.ReportPropertyBean;
import com.nomagic.magicdraw.magicreport.ui.bean.TemplateBean;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.magicdraw.uml.symbols.paths.PathElement;
import com.nomagic.magicdraw.uml.symbols.shapes.ShapeElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Namespace;
import com.nomagic.uml2.ext.magicdraw.interactions.mdbasicinteractions.Interaction;
import com.nomagic.uml2.ext.magicdraw.interactions.mdbasicinteractions.Lifeline;
import com.nomagic.uml2.ext.magicdraw.interactions.mdbasicinteractions.Message;
import com.nomagic.uml2.ext.magicdraw.interactions.mdbasicinteractions.MessageSort;
import com.nomagic.uml2.ext.magicdraw.interactions.mdbasicinteractions.MessageSortEnum;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

import static com.cameomcp.plugin.tools.Tool.Mode.*;

/** Relation maps, Report Wizard generation and sequence-diagram messages (UML/SysML v1 projects). */
final class ExtraTools {

    private ExtraTools() {}

    static final String RELATION_MAP = "Relation Map Diagram";
    private static final String RM_PKG = "com.nomagic.magicdraw.visualization.relationshipmap.";

    static void register(Map<String, Tool> m) {
        // ---------------------------------------------------------------- relation maps
        Tools.add(m, "cameo_create_relation_map", WRITE, c -> {
            Project p = c.project();
            Element context = c.el("contextId");
            Element owner = c.has("ownerId") ? c.el("ownerId") : Model.nearestPackage(context);
            if (!(owner instanceof Namespace ns)) throw new ToolError("ownerId must be a package or other namespace");
            Diagram d = ModelElementsManager.getInstance().createDiagram(RELATION_MAP, ns);
            if (d == null) throw new ToolError("Cameo could not create a Relation Map here (is the Relation Map plugin enabled?)");
            d.setName(c.str("name", "Relation Map of " + Model.name(context)));
            configureMap(d, context, c.i("depth", 3), c.els("scopeIds"), c.els("elementTypeIds"));
            return Model.summary(d).put("contextId", context.getID()).put("depth", c.i("depth", 3));
        });

        Tools.add(m, "cameo_refresh_relation_map", WRITE, c -> {
            Element e = c.el("diagramId");
            if (!(e instanceof Diagram d)) throw new ToolError("Not a diagram");
            if (c.has("contextId") || c.has("depth")) {
                Object s = settings(d);
                Element ctx = c.has("contextId") ? c.el("contextId") : (Element) call(s, "getContextElement");
                configureMap(d, ctx, c.i("depth", (Integer) call(s, "getDepth")), c.els("scopeIds"), c.els("elementTypeIds"));
            } else {
                Class.forName(RM_PKG + "RelationshipMapUtilities").getMethod("refreshMap", Diagram.class).invoke(null, d);
            }
            DiagramPresentationElement dpe = c.project().getDiagram(d);
            dpe.ensureLoaded();
            int[] shapes = {0};
            DiagramTools.forEachPe(dpe, pe -> { if (pe instanceof ShapeElement) shapes[0]++; });
            return Model.summary(d).put("shapes", shapes[0]);
        });

        // ---------------------------------------------------------------- reports
        Tools.add(m, "cameo_generate_report", APP, c -> {
            Project p = c.project();
            String name = c.str("template");
            TemplateBean shared = null;
            for (TemplateBean x : TemplateHelper.listTemplates(false)) if (name.equalsIgnoreCase(x.getName())) shared = x;
            if (shared == null) throw new ToolError("No report template '" + name + "'; see cameo_list_report_templates");
            // Work on copies: the beans Report Wizard returns are the ones its own dialog uses and saves.
            TemplateBean t = (TemplateBean) shared.clone();
            ReportBean r = null;
            if (c.has("report")) for (ReportBean x : shared.getReportList()) if (c.str("report").equalsIgnoreCase(x.getName())) r = x;
            if (r == null) r = shared.getDefaultReport() != null ? shared.getDefaultReport() : shared.getReportList().isEmpty() ? null : shared.getReportList().get(0);
            r = r == null ? new ReportBean() : (ReportBean) r.clone();
            Set<Element> scope = new LinkedHashSet<>(c.els("scopeIds"));
            if (scope.isEmpty()) scope.add(c.model());
            PackageSelectionBean sel = new PackageSelectionBean();
            sel.setPackages(scope);
            sel.setPackageIds(scope.stream().map(Element::getID).toArray(String[]::new));
            sel.setSelectRecursive(c.b("recursive", true));
            r.setSelectedPackage(sel);
            File out = new File(c.str("outputFile"));
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            ReportPropertyBean props = r.getReportProperty() != null ? (ReportPropertyBean) r.getReportProperty().clone() : new ReportPropertyBean();
            props.setOutputFile(out.getAbsolutePath());
            props.setDisplayInViewer(false);
            r.setReportProperty(props);
            t.setSelectedReport(r);
            GenerateTask task = new GenerateTask(t);
            task.setProperty("template.output.file", out.getName());
            task.setProperty("template.output.location", out.getAbsoluteFile().getParent());
            task.setProperty("skipProgressbar", "true");
            task.execute();
            if (!out.isFile()) throw new ToolError("Report Wizard finished without writing " + out + " (check the template's output format matches the file extension)");
            return Catalog.JSON.createObjectNode().put("file", out.getAbsolutePath()).put("bytes", out.length())
                    .put("template", t.getName()).put("report", r.getName());
        });

        // ---------------------------------------------------------------- sequence messages
        Tools.add(m, "cameo_add_sequence_message", WRITE, c -> {
            Project p = c.project();
            DiagramPresentationElement d = DiagramTools.dpe(c);
            Element from = c.el("fromLifelineId"), to = c.el("toLifelineId");
            if (!(from instanceof Lifeline lf) || !(to instanceof Lifeline lt)) throw new ToolError("fromLifelineId and toLifelineId must be lifelines");
            Interaction owner = lf.getInteraction();
            Message msg = p.getElementsFactory().createMessageInstance();
            msg.setName(c.str("name", ""));
            ModelElementsManager.getInstance().addElement(msg, owner);
            if (c.has("signatureId")) msg.setSignature((NamedElement) c.el("signatureId"));
            MessageSort sort = switch (c.str("sort", "synchCall").toLowerCase(Locale.ROOT)) {
                case "synchcall", "sync", "call" -> MessageSortEnum.SYNCHCALL;
                case "asynchcall", "async" -> MessageSortEnum.ASYNCHCALL;
                case "asynchsignal", "signal" -> MessageSortEnum.ASYNCHSIGNAL;
                case "reply", "return" -> MessageSortEnum.REPLY;
                case "create", "createmessage" -> MessageSortEnum.CREATEMESSAGE;
                case "delete", "deletemessage" -> MessageSortEnum.DELETEMESSAGE;
                default -> throw new ToolError("sort: synchCall, asynchCall, asynchSignal, reply, create or delete");
            };
            msg.setMessageSort(sort);
            Message after = c.has("afterMessageId") ? (Message) c.el("afterMessageId") : null;
            PresentationElement a = DiagramTools.shapeOf(d, from), b = DiagramTools.shapeOf(d, to);
            PathElement path = PresentationElementsManager.getInstance().createSequenceMessage(msg, sort, (ShapeElement) a, (ShapeElement) b,
                    from == to, 0, after, c.i("verticalGap", 30));
            ObjectNode o = Model.ref(msg).put("sort", sort.toString());
            if (path != null) o.set("symbol", DiagramTools.peJson(path));
            return o;
        });
    }

    // ------------------------------------------------------------------ relation map settings (plugin is optional: reflection)

    private static Object settings(Diagram d) throws Exception {
        return Class.forName(RM_PKG + "model.settings.GraphSettings").getConstructor(Diagram.class).newInstance(d);
    }

    private static void configureMap(Diagram d, Element context, int depth, List<Element> scope, List<Element> types) throws Exception {
        Object s;
        try {
            s = settings(d);
        } catch (ClassNotFoundException e) {
            throw new ToolError("The Relation Map plugin is not loaded in this Cameo");
        }
        Class<?> k = s.getClass();
        k.getMethod("setContextElement", Element.class).invoke(s, context);
        k.getMethod("setDepth", int.class).invoke(s, depth);
        if (!scope.isEmpty()) k.getMethod("setScopeRoots", Collection.class).invoke(s, new ArrayList<>(scope));
        if (!types.isEmpty()) k.getMethod("setElementTypes", List.class).invoke(s, new ArrayList<>(types));
        Class.forName(RM_PKG + "RelationshipMapUtilities").getMethod("refreshMap", Diagram.class).invoke(null, d);
    }

    private static Object call(Object o, String method) throws Exception {
        Method mm = o.getClass().getMethod(method);
        return mm.invoke(o);
    }
}
