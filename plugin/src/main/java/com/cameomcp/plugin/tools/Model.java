package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.uml2.ext.jmi.helpers.CoreHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.jmi.helpers.TagsHelper;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityEdge;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;
import com.nomagic.uml2.ext.magicdraw.statemachines.mdbehaviorstatemachines.Region;
import com.nomagic.uml2.ext.magicdraw.statemachines.mdbehaviorstatemachines.State;
import com.nomagic.uml2.ext.magicdraw.statemachines.mdbehaviorstatemachines.StateMachine;
import com.nomagic.uml2.ext.magicdraw.statemachines.mdbehaviorstatemachines.Transition;
import com.nomagic.uml2.ext.magicdraw.statemachines.mdbehaviorstatemachines.Vertex;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/** Element <-> JSON, creation by type name, relationships, and generic property setting. */
public final class Model {

    private Model() {}

    /** SysML / convenience type -> {UML metaclass, stereotype or null, pseudostate kind or null}. */
    private static final Map<String, String[]> ALIASES = new HashMap<>();
    static {
        String[][] a = {
            {"Block", "Class", "Block"}, {"InterfaceBlock", "Class", "InterfaceBlock"},
            {"ConstraintBlock", "Class", "ConstraintBlock"}, {"Requirement", "Class", "Requirement"},
            {"ValueType", "DataType", "ValueType"}, {"FlowProperty", "Property", "FlowProperty"},
            {"ProxyPort", "Port", "ProxyPort"}, {"FullPort", "Port", "FullPort"},
            {"TestCase", "Activity", "TestCase"}, {"Unit", "InstanceSpecification", "Unit"},
            {"QuantityKind", "InstanceSpecification", "QuantityKind"},
            {"Satisfy", "Abstraction", "Satisfy"}, {"Verify", "Abstraction", "Verify"},
            {"DeriveReqt", "Abstraction", "DeriveReqt"}, {"Refine", "Abstraction", "Refine"},
            {"Trace", "Abstraction", "Trace"}, {"Copy", "Abstraction", "Copy"},
            {"Allocate", "Abstraction", "Allocate"},
            {"Composition", "Association", null}, {"Aggregation", "Association", null},
            {"InitialState", "Pseudostate", null, "initial"}, {"Initial", "Pseudostate", null, "initial"},
            {"Choice", "Pseudostate", null, "choice"}, {"Junction", "Pseudostate", null, "junction"},
            {"Fork", "Pseudostate", null, "fork"}, {"Join", "Pseudostate", null, "join"},
            {"ShallowHistory", "Pseudostate", null, "shallowHistory"}, {"DeepHistory", "Pseudostate", null, "deepHistory"},
            {"EntryPoint", "Pseudostate", null, "entryPoint"}, {"ExitPoint", "Pseudostate", null, "exitPoint"},
            {"Terminate", "Pseudostate", null, "terminate"},
        };
        for (String[] r : a) ALIASES.put(r[0].toLowerCase(Locale.ROOT), r);
    }

    // ------------------------------------------------------------------ JSON views

    public static String id(Element e) { return e == null ? null : e.getID(); }

    public static String name(Element e) {
        return e instanceof NamedElement n ? n.getName() : null;
    }

    /** Short reference: id, name, type, stereotypes. */
    public static ObjectNode ref(Element e) {
        ObjectNode o = Catalog.JSON.createObjectNode().put("id", e.getID());
        String n = name(e);
        if (n != null) o.put("name", n);
        o.put("type", e.getHumanType());
        List<Stereotype> st = StereotypesHelper.getStereotypes(e);
        if (!st.isEmpty()) {
            ArrayNode a = o.putArray("stereotypes");
            st.forEach(s -> a.add(s.getName()));
        }
        return o;
    }

    public static ObjectNode summary(Element e) {
        ObjectNode o = ref(e);
        if (e instanceof NamedElement n && n.getQualifiedName() != null) o.put("qualifiedName", n.getQualifiedName());
        if (e.getOwner() != null) o.put("ownerId", e.getOwner().getID());
        return o;
    }

    /** Everything an agent normally needs: summary, documentation, UML properties, tagged values. */
    public static ObjectNode details(Element e) {
        ObjectNode o = summary(e);
        String doc = CoreHelper.getComment(e);
        if (doc != null && !doc.isEmpty()) o.put("documentation", doc);
        ObjectNode props = o.putObject("properties");
        for (EStructuralFeature f : e.eClass().getEAllStructuralFeatures()) {
            if (f.isDerived() || f.isTransient() || f.getName().startsWith("_") || SKIP.contains(f.getName())) continue;
            Object v;
            try {
                v = e.eGet(f);
            } catch (RuntimeException ex) {
                continue;
            }
            JsonNode j = toJson(v);
            if (j != null) props.set(f.getName(), j);
        }
        ObjectNode tags = Catalog.JSON.createObjectNode();
        for (Stereotype s : StereotypesHelper.getStereotypes(e)) {
            for (Property tag : TagsHelper.getPropertiesWithDerivedOrdered(s)) {
                if (tag.getName() == null || tag.getName().startsWith("base_") || tag.getName().startsWith("extension_")) continue;
                List<?> vals = TagsHelper.getStereotypePropertyValue(e, s, tag.getName());
                if (vals == null || vals.isEmpty()) continue;
                ArrayNode arr = Catalog.JSON.createArrayNode();
                vals.forEach(v -> { JsonNode j = toJson(v); if (j != null) arr.add(j); });
                tags.set(tag.getName(), arr.size() == 1 ? arr.get(0) : arr);
            }
        }
        if (!tags.isEmpty()) o.set("taggedValues", tags);
        return o;
    }

    private static final Set<String> SKIP = Set.of("ownedComment", "ownedElement", "appliedStereotypeInstance",
            "syncElement", "ID", "nameExpression", "templateParameter", "owningTemplateParameter", "taggedValue",
            "appliedStereotype", "mdExtensions");

    private static JsonNode toJson(Object v) {
        if (v == null) return null;
        if (v instanceof Element el) return ref(el);
        if (v instanceof String s) return s.isEmpty() ? null : Catalog.JSON.getNodeFactory().textNode(s);
        if (v instanceof Boolean || v instanceof Number) return Catalog.JSON.valueToTree(v);
        if (v instanceof Enumerator en) return Catalog.JSON.getNodeFactory().textNode(en.getLiteral());
        if (v instanceof Collection<?> c) {
            if (c.isEmpty()) return null;
            ArrayNode a = Catalog.JSON.createArrayNode();
            int n = 0;
            for (Object x : c) {
                if (n++ == 100) { a.add("... " + (c.size() - 100) + " more"); break; }
                JsonNode j = toJson(x);
                if (j != null) a.add(j);
            }
            return a.isEmpty() ? null : a;
        }
        return null;
    }

    // ------------------------------------------------------------------ lookup

    public static Stereotype stereotype(Project p, String name) {
        Stereotype best = null;
        for (Stereotype s : StereotypesHelper.getAllStereotypes(p)) {
            if (!name.equalsIgnoreCase(s.getName())) continue;
            if (best == null) best = s;
            Element prof = StereotypesHelper.getProfileForStereotype(s);
            if (prof instanceof NamedElement n && n.getName() != null && n.getName().contains("SysML")) return s;
        }
        if (best == null) throw new ToolError("No stereotype named '" + name + "' in this project (is the profile used?)");
        return best;
    }

    public static Package nearestPackage(Element e) {
        Element x = e;
        while (x != null && !(x instanceof Package)) x = x.getOwner();
        return (Package) x;
    }

    public static void walk(Element root, Consumer<Element> visit) {
        Deque<Element> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Element e = stack.pop();
            visit.accept(e);
            for (Element c : e.getOwnedElement()) stack.push(c);
        }
    }

    // ------------------------------------------------------------------ creation

    /** Creates an element of a UML metaclass ("Class", "Package", ...) or alias ("Block", "Requirement", "InitialState"...). */
    public static Element create(Project p, String type, Element parent, String name) {
        String[] alias = ALIASES.get(type.toLowerCase(Locale.ROOT));
        String meta = alias != null ? alias[1] : type;
        Element e = instantiate(p, meta);
        if (alias != null && alias.length > 3) set(p, e, "kind", Catalog.JSON.getNodeFactory().textNode(alias[3]));
        if (name != null && e instanceof NamedElement n) n.setName(name);
        if (parent != null) addTo(p, e, parent);
        if (alias != null && alias[2] != null) StereotypesHelper.addStereotype(e, stereotype(p, alias[2]));
        return e;
    }

    static Element instantiate(Project p, String meta) {
        String cls = Character.toUpperCase(meta.charAt(0)) + meta.substring(1);
        try {
            Method m = p.getElementsFactory().getClass().getMethod("create" + cls + "Instance");
            return (Element) m.invoke(p.getElementsFactory());
        } catch (NoSuchMethodException ex) {
            throw new ToolError("Unknown element type '" + meta + "'. Use a UML metaclass name (Class, Package, Property, Port, "
                    + "Activity, State, ...) or a SysML type (Block, Requirement, ValueType, InterfaceBlock, ConstraintBlock, "
                    + "FlowProperty, ProxyPort, FullPort, TestCase, InitialState, Choice, ...).");
        } catch (ReflectiveOperationException ex) {
            throw new ToolError("Cannot create " + meta + ": " + ex.getCause());
        }
    }

    /** Adds e under parent; vertices go into the parent's first region (created if missing). */
    static void addTo(Project p, Element e, Element parent) {
        Element owner = parent;
        if (e instanceof Vertex && (parent instanceof StateMachine || parent instanceof State)) {
            Collection<Region> regions = parent instanceof StateMachine sm ? sm.getRegion() : ((State) parent).getRegion();
            if (regions.isEmpty()) {
                Region r = p.getElementsFactory().createRegionInstance();
                add(r, parent);
                owner = r;
            } else {
                owner = regions.iterator().next();
            }
        }
        add(e, owner);
    }

    private static void add(Element e, Element owner) {
        try {
            ModelElementsManager.getInstance().addElement(e, owner);
        } catch (Exception ex) {
            throw new ToolError("Cannot add " + e.getHumanType() + " under " + owner.getHumanType() + " '" + name(owner) + "': " + ex.getMessage());
        }
    }

    /** Creates a relationship of the given type from source (client) to target (supplier). */
    public static Element relate(Project p, String type, Element source, Element target, String name) {
        String key = type.toLowerCase(Locale.ROOT);
        String[] alias = ALIASES.get(key);
        String meta = alias != null ? alias[1] : type;
        Element rel = instantiate(p, meta);
        if (!(rel instanceof Relationship || rel instanceof ActivityEdge || rel instanceof Transition || rel instanceof Connector))
            throw new ToolError("'" + type + "' is not a relationship type");
        if (name != null && rel instanceof NamedElement n) n.setName(name);
        Element owner;
        if (rel instanceof ActivityEdge && source instanceof ActivityNode an && an.getActivity() != null) owner = an.getActivity();
        else if (rel instanceof Transition && source instanceof Vertex v && v.getContainer() != null) owner = v.getContainer();
        else if (rel instanceof Connector) owner = source.getOwner();
        else if (CoreHelper.isRelationshipAlwaysInClient(rel.getClassType())) owner = source;
        else owner = nearestPackage(source);
        if (owner == null) owner = p.getPrimaryModel();
        add(rel, owner);
        CoreHelper.setClientElement(rel, source);
        CoreHelper.setSupplierElement(rel, target);
        if (alias != null && alias[2] != null) StereotypesHelper.addStereotype(rel, stereotype(p, alias[2]));
        if (rel instanceof Association as && (key.equals("composition") || key.equals("aggregation"))) {
            for (Property end : as.getMemberEnd()) {
                if (end.getType() == target) {
                    set(p, end, "aggregation", Catalog.JSON.getNodeFactory().textNode(key.equals("composition") ? "composite" : "shared"));
                }
            }
        }
        return rel;
    }

    // ------------------------------------------------------------------ properties

    /**
     * Sets one property: special keys (name, documentation, multiplicity, defaultValue), any UML feature
     * of the element (attributes by value, references by element id), or a tagged value of an applied stereotype.
     */
    public static void set(Project p, Element e, String key, JsonNode value) {
        switch (key) {
            case "name" -> {
                if (!(e instanceof NamedElement n)) throw new ToolError(e.getHumanType() + " has no name");
                n.setName(value.asText());
                return;
            }
            case "documentation" -> { CoreHelper.setComment(e, value.asText()); return; }
            case "multiplicity" -> {
                if (!(e instanceof MultiplicityElement me)) throw new ToolError(e.getHumanType() + " has no multiplicity");
                CoreHelper.setMultiplicity(value.asText(), me);
                return;
            }
            case "defaultValue" -> {
                if (!(e instanceof Property prop)) throw new ToolError("defaultValue applies to properties");
                prop.setDefaultValue(valueSpec(p, value));
                return;
            }
            default -> { }
        }
        EStructuralFeature f = e.eClass().getEStructuralFeature(key);
        if (f != null && f.isChangeable() && !f.isDerived()) {
            try {
                if (f.isMany()) {
                    @SuppressWarnings("unchecked")
                    Collection<Object> list = (Collection<Object>) e.eGet(f);
                    list.clear();
                    if (value.isArray()) for (JsonNode v : value) list.add(convert(p, f, v));
                    else if (!value.isNull()) list.add(convert(p, f, value));
                } else {
                    e.eSet(f, value.isNull() ? null : convert(p, f, value));
                }
            } catch (ToolError te) {
                throw te;
            } catch (RuntimeException ex) {
                throw new ToolError("Cannot set " + key + " on " + e.getHumanType() + ": " + ex.getMessage());
            }
            return;
        }
        for (Stereotype s : StereotypesHelper.getStereotypes(e)) {
            if (TagsHelper.getPropertyByName(s, key) != null) {
                Object v = tagValue(p, value);
                if (v instanceof List<?> l) {
                    TagsHelper.clearStereotypeProperty(e, s, key);
                    for (Object x : l) TagsHelper.setStereotypePropertyValue(e, s, key, x, true);
                } else {
                    TagsHelper.setStereotypePropertyValue(e, s, key, v);
                }
                return;
            }
        }
        throw new ToolError(e.getHumanType() + " has no settable property or tagged value named '" + key + "'");
    }

    private static Object convert(Project p, EStructuralFeature f, JsonNode v) {
        EClassifier t = f.getEType();
        if (f instanceof EReference) {
            Object target = p.getElementByID(v.asText());
            if (target == null) throw new ToolError("No element with id '" + v.asText() + "' for property " + f.getName());
            if (!t.isInstance(target)) throw new ToolError(f.getName() + " needs a " + t.getName() + ", not " + ((Element) target).getHumanType());
            return target;
        }
        if (t instanceof EEnum en) {
            EEnumLiteral lit = en.getEEnumLiteralByLiteral(v.asText());
            if (lit == null) lit = en.getEEnumLiteral(v.asText());
            if (lit == null) {
                List<String> names = new ArrayList<>();
                en.getELiterals().forEach(l -> names.add(l.getLiteral()));
                throw new ToolError(f.getName() + " must be one of " + names);
            }
            return lit.getInstance();
        }
        java.lang.Class<?> c = t.getInstanceClass();
        if (c == boolean.class || c == Boolean.class) return v.asBoolean();
        if (c == int.class || c == Integer.class) return v.asInt();
        if (c == double.class || c == Double.class) return v.asDouble();
        if (c == long.class || c == Long.class) return v.asLong();
        return v.asText();
    }

    private static Object tagValue(Project p, JsonNode v) {
        if (v.isArray()) {
            List<Object> l = new ArrayList<>();
            v.forEach(x -> l.add(tagValue(p, x)));
            return l;
        }
        if (v.isBoolean()) return v.asBoolean();
        if (v.isInt()) return v.asInt();
        if (v.isNumber()) return v.asDouble();
        Object el = p.getElementByID(v.asText());
        return el != null ? el : v.asText();
    }

    static ValueSpecification valueSpec(Project p, JsonNode v) {
        var f = p.getElementsFactory();
        if (v.isBoolean()) { var b = f.createLiteralBooleanInstance(); b.setValue(v.asBoolean()); return b; }
        if (v.isIntegralNumber()) { var i = f.createLiteralIntegerInstance(); i.setValue(v.asInt()); return i; }
        if (v.isNumber()) { var r = f.createLiteralRealInstance(); r.setValue(v.asDouble()); return r; }
        var s = f.createLiteralStringInstance();
        s.setValue(v.asText());
        return s;
    }

    /** Relationships attached to e: direction "out" (e is client/source), "in", or "both". */
    public static List<Element> relationships(Element e, String direction) {
        List<Element> out = new ArrayList<>();
        Set<Element> seen = new HashSet<>();
        for (Relationship r : e.get_relationshipOfRelatedElement()) consider(e, r, direction, out, seen);
        if (e instanceof ActivityNode n) {
            if (!"in".equals(direction)) n.getOutgoing().forEach(x -> { if (seen.add(x)) out.add(x); });
            if (!"out".equals(direction)) n.getIncoming().forEach(x -> { if (seen.add(x)) out.add(x); });
        }
        if (e instanceof Vertex v) {
            if (!"in".equals(direction)) v.getOutgoing().forEach(x -> { if (seen.add(x)) out.add(x); });
            if (!"out".equals(direction)) v.getIncoming().forEach(x -> { if (seen.add(x)) out.add(x); });
        }
        return out;
    }

    private static void consider(Element e, Element r, String direction, List<Element> out, Set<Element> seen) {
        boolean isClient = CoreHelper.getClientElement(r) == e;
        boolean ok = "both".equals(direction) || ("out".equals(direction) == isClient);
        if (ok && seen.add(r)) out.add(r);
    }

    public static ObjectNode relJson(Element r) {
        ObjectNode o = ref(r);
        Element c = CoreHelper.getClientElement(r);
        Element s = CoreHelper.getSupplierElement(r);
        if (c != null) o.set("source", ref(c));
        if (s != null) o.set("target", ref(s));
        return o;
    }
}
