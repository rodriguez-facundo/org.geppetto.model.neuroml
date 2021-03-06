
package org.geppetto.model.neuroml.visualUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.ModelInterpreterException;
import org.geppetto.model.neuroml.modelInterpreterUtils.NeuroMLModelInterpreterUtils;
import org.geppetto.model.neuroml.utils.CellUtils;
import org.geppetto.model.neuroml.utils.Resources;
import org.geppetto.model.types.CompositeVisualType;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.types.VisualType;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.Cylinder;
import org.geppetto.model.values.Point;
import org.geppetto.model.values.Sphere;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.values.VisualGroup;
import org.geppetto.model.values.VisualGroupElement;
import org.geppetto.model.values.VisualValue;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;
import org.lemsml.jlems.core.sim.LEMSException;
import org.neuroml.model.Cell;
import org.neuroml.model.Include;
import org.neuroml.model.Morphology;
import org.neuroml.model.Point3DWithDiam;
import org.neuroml.model.Segment;
import org.neuroml.model.SegmentGroup;
import org.neuroml.model.util.NeuroMLException;

/**
 * Helper class to populate visualization tree for neuroml models
 * 
 */
public class ExtractVisualType
{

	private static Log _logger = LogFactory.getLog(ExtractVisualType.class);

	private Cell cell;

	private TypesFactory typeFactory = TypesFactory.eINSTANCE;
	private ValuesFactory valuesFactory = ValuesFactory.eINSTANCE;
	private VariablesFactory variablesFactory = VariablesFactory.eINSTANCE;

	private Map<String, List<VisualGroupElement>> segmentsMap = new HashMap<String, List<VisualGroupElement>>();
	private GeppettoModelAccess access;

	private LinkedHashMap<String, List<Segment>> segmentGroupSegMap;

	private List<Variable> visualObjectsSegments;

	private Map<String, List<Variable>> segmentGeometries = new HashMap<String, List<Variable>>();

	public ExtractVisualType(Cell cell, GeppettoModelAccess access) throws LEMSException, NeuroMLException
	{
		super();

		this.access = access;
		this.cell = cell;

		// AQP Maybe we can initialise cellutils here and pass this variable to the create density class
		CellUtils cellUtils = new CellUtils(cell);
		segmentGroupSegMap = cellUtils.getSegmentGroupsVsSegs();
	}

	public VisualType createTypeFromCellMorphology() throws GeppettoVisitingException, LEMSException, NeuroMLException, ModelInterpreterException
	{
		long start = System.currentTimeMillis();

		CompositeVisualType visualCompositeType = typeFactory.createCompositeVisualType();
		NeuroMLModelInterpreterUtils.initialiseNodeFromString(visualCompositeType, cell.getId()+"__"+cell.getMorphology().getId());

		VisualGroup visualGroupCellParts = createCellPartsVisualGroups();
		if(visualGroupCellParts != null)
		{
			visualCompositeType.getVisualGroups().add(visualGroupCellParts);
		}

		Map<Integer, Variable> segmentIdsvisualObjectsSegments = getVisualObjectsFromListOfSegments();
		visualObjectsSegments = new ArrayList<Variable>(segmentIdsvisualObjectsSegments.values());

		if(cell.getMorphology().getSegmentGroup().isEmpty())
		{
			visualCompositeType.getVariables().addAll(visualObjectsSegments);
		}
		else
		{
			visualCompositeType.getVariables().addAll(createNodesFromMorphologyBySegmentGroup());

			// create density groups for each cell, if it has some
			PopulateChannelDensityVisualGroups populateChannelDensityVisualGroups = new PopulateChannelDensityVisualGroups(cell, segmentGroupSegMap, segmentIdsvisualObjectsSegments, this.access);
			visualCompositeType.getVisualGroups().addAll(populateChannelDensityVisualGroups.createChannelDensities());

			if(populateChannelDensityVisualGroups.getChannelDensityTag() != null) access.addTag(populateChannelDensityVisualGroups.getChannelDensityTag());
		}

		_logger.info("Creating morphology for " + cell.getMorphology().getId() + ", took " + (System.currentTimeMillis() - start) + "ms");

		return visualCompositeType;
	}

	/**
	 * @param allSegments
	 * @param list
	 * @param list2
	 * @param id
	 * @return
	 * @throws GeppettoVisitingException
	 */
	private Map<Integer, Variable> getVisualObjectsFromListOfSegments() throws GeppettoVisitingException
	{
		// List<Variable> visualObjectVariables = new ArrayList<Variable>();
		Map<Integer, Variable> visualObjectVariables = new HashMap<Integer, Variable>();

		Map<String, Point3DWithDiam> distalPoints = new HashMap<String, Point3DWithDiam>();
		for(Segment segment : cell.getMorphology().getSegment())
		{
			Variable variable = variablesFactory.createVariable();

			NeuroMLModelInterpreterUtils.initialiseNodeFromString(variable, NeuroMLModelInterpreterUtils.getVisualObjectIdentifier(segment));

			variable.getTypes().add(this.access.getType(TypesPackage.Literals.VISUAL_TYPE));

			String idSegmentParent = null;
			Point3DWithDiam parentDistal = null;
			if(segment.getParent() != null)
			{
				idSegmentParent = segment.getParent().getSegment().toString();
			}
			if(distalPoints.containsKey(idSegmentParent))
			{
				parentDistal = distalPoints.get(idSegmentParent);
			}
			VisualValue visualObject = getVisualObjectFromSegment(segment, parentDistal);
			if(segmentsMap.containsKey(variable.getId()))
			{
				// get groups list for segment and put it in visual objects
				visualObject.getGroupElements().addAll(segmentsMap.get(variable.getId()));
			}

			variable.getInitialValues().put(this.access.getType(TypesPackage.Literals.VISUAL_TYPE), visualObject);

			distalPoints.put(segment.getId().toString(), segment.getDistal());
			visualObjectVariables.put(segment.getId(), variable);
		}

		return visualObjectVariables;
	}

	/**
	 * @param location
	 * @param visualizationTree
	 * @param list
	 * @return
	 * @throws GeppettoVisitingException
	 */
	private List<Variable> createNodesFromMorphologyBySegmentGroup() throws GeppettoVisitingException
	{
		List<Variable> visualObjectVariables = new ArrayList<Variable>();

		Morphology morphology = cell.getMorphology();
		if(!morphology.getSegmentGroup().isEmpty())
		{
			Map<String, List<String>> subgroupsMap = new HashMap<String, List<String>>();
			for(SegmentGroup sg : morphology.getSegmentGroup())
			{
				for(Include include : sg.getInclude())
				{
					// the map is <containedGroup,containerGroup>
					if(!subgroupsMap.containsKey(include.getSegmentGroup()))
					{
						subgroupsMap.put(include.getSegmentGroup(), new ArrayList<String>());
					}
					subgroupsMap.get(include.getSegmentGroup()).add(sg.getId());
				}
				if(!sg.getMember().isEmpty())
				{
					segmentGeometries.put(sg.getId(), getVisualObjectsForGroup(sg.getId(), visualObjectsSegments));
				}
			}

			// this adds all segment groups not contained in the macro groups if any
			for(String sgId : segmentGeometries.keySet())
			{
				visualObjectVariables.addAll(segmentGeometries.get(sgId));
			}
		}

		return visualObjectVariables;
	}

	/**
	 * Gets all segments group from cell. Creates a map with segments as key of map, and list of groups it belongs as value. Creates visual groups for cell regions while looping through segment
	 * groups.
	 * 
	 * @param segmentsGroup
	 * @param visualizationTree
	 * @return
	 */
	private VisualGroup createCellPartsVisualGroups()
	{
		VisualGroup cellParts = valuesFactory.createVisualGroup();
		NeuroMLModelInterpreterUtils.initialiseNodeFromString(cellParts, Resources.CELL_REGIONS.get());

		// Get all the segment groups from morphology
		for(SegmentGroup segmentGroup : this.cell.getMorphology().getSegmentGroup())
		{
			// segment found
			String segmentGroupID = segmentGroup.getId();

			// create visual groups for cell regions
			if(segmentGroupID.equals(Resources.SOMA.getId()) || segmentGroupID.equals(Resources.DENDRITES.getId()) || segmentGroupID.equals(Resources.AXONS.getId()))
			{
				VisualGroupElement visualGroupElement = valuesFactory.createVisualGroupElement();
				NeuroMLModelInterpreterUtils.initialiseNodeFromString(visualGroupElement, segmentGroupID);

				if(segmentGroupID.equals(Resources.SOMA.getId()))
				{
					visualGroupElement.setDefaultColor(ModelInterpreterVisualConstants.SOMA_COLOR);
				}
				else if(segmentGroupID.equals(Resources.DENDRITES.getId()))
				{
					visualGroupElement.setDefaultColor(ModelInterpreterVisualConstants.DENDRITES_COLOR);
				}
				else if(segmentGroupID.equals(Resources.AXONS.getId()))
				{
					visualGroupElement.setDefaultColor(ModelInterpreterVisualConstants.AXONS_COLOR);
				}
				cellParts.getVisualGroupElements().add(visualGroupElement);

				for(Segment segment : segmentGroupSegMap.get(segmentGroup.getId()))
				{
					String segmentID = NeuroMLModelInterpreterUtils.getVisualObjectIdentifier(segment);
					List<VisualGroupElement> groups;
					// segment not in map, add with new list for groups
					if(!segmentsMap.containsKey(segmentID))
					{
						groups = new ArrayList<VisualGroupElement>();
						groups.add(visualGroupElement);
						segmentsMap.put(segmentID, groups);
					}
					// segment in map, get list and put with updated one for groups
					else
					{
						groups = segmentsMap.get(segmentID);
						groups.add(visualGroupElement);
						segmentsMap.put(segmentID, groups);
					}
					segmentsMap.put(segmentID, groups);
				}
			}

		}

		if(cellParts.getVisualGroupElements().size() == 0)
		{
			cellParts = null;
		}

		return cellParts;
	}

	/**
	 * @param sg
	 * @param allSegments
	 * @return
	 */
	private List<Variable> getVisualObjectsForGroup(String sg, List<Variable> allSegments)
	{
		List<Variable> geometries = new ArrayList<Variable>();

		for(Segment segment : segmentGroupSegMap.get(sg))
		{
			for(Variable g : allSegments)
			{
				if(g.getId().equals(NeuroMLModelInterpreterUtils.getVisualObjectIdentifier(segment)))
				{
					geometries.add(g);
				}
			}
		}

		return geometries;
	}

	/**
	 * @param p1
	 * @param p2
	 * @return
	 */
	private boolean samePoint(Point3DWithDiam p1, Point3DWithDiam p2)
	{
		return p1.getX() == p2.getX() && p1.getY() == p2.getY() && p1.getZ() == p2.getZ() && p1.getDiameter() == p2.getDiameter();
	}

	/**
	 * @param s
	 * @param parentDistal
	 * @param visualGroupNode
	 * @return
	 */
	private VisualValue getVisualObjectFromSegment(Segment s, Point3DWithDiam parentDistal)
	{
		Point3DWithDiam proximal = (s.getProximal() == null) ? parentDistal : s.getProximal();
		Point3DWithDiam distal = s.getDistal();

		if(samePoint(proximal, distal)) // ideally an equals but the objects
										// are generated. hassle postponed.
		{
			Sphere sphere = valuesFactory.createSphere();
			sphere.setRadius(proximal.getDiameter() / 2);
			sphere.setPosition(getPoint(proximal));
			return sphere;
		}
		else
		{
			Cylinder cylinder = valuesFactory.createCylinder();
			if(proximal != null)
			{
				cylinder.setPosition(getPoint(proximal));
				cylinder.setBottomRadius(proximal.getDiameter() / 2);
			}
			if(distal != null)
			{
				cylinder.setTopRadius(s.getDistal().getDiameter() / 2);
				cylinder.setDistal(getPoint(distal));
				cylinder.setHeight(0d);
			}
			return cylinder;
		}
	}

	/**
	 * @param distal
	 * @return
	 */
	private Point getPoint(Point3DWithDiam distal)
	{
		Point point = valuesFactory.createPoint();
		point.setX(distal.getX());
		point.setY(distal.getY());
		point.setZ(distal.getZ());
		return point;
	}

	public List<Variable> getVisualObjectsSegments()
	{
		return visualObjectsSegments;
	}

}