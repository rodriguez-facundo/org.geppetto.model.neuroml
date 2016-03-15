package org.geppetto.model.neuroml.modelInterpreterUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.ModelInterpreterException;
import org.geppetto.model.neuroml.utils.ModelInterpreterUtils;
import org.geppetto.model.neuroml.utils.Resources;
import org.geppetto.model.neuroml.utils.ResourcesDomainType;
import org.geppetto.model.neuroml.visualUtils.ExtractVisualType;
import org.geppetto.model.types.ArrayType;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.CompositeVisualType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.types.VisualType;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.Point;
import org.geppetto.model.values.Sphere;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;
import org.lemsml.jlems.core.sim.LEMSException;
import org.lemsml.jlems.core.type.Attribute;
import org.lemsml.jlems.core.type.Component;
import org.lemsml.jlems.core.type.Exposure;
import org.lemsml.jlems.core.type.ParamValue;
import org.neuroml.export.utils.Utils;
import org.neuroml.model.util.NeuroMLException;

public class PopulateTypes
{

	private Map<String, Type> types;

	private TypesFactory typesFactory = TypesFactory.eINSTANCE;
	private ValuesFactory valuesFactory = ValuesFactory.eINSTANCE;
	private VariablesFactory variablesFactory = VariablesFactory.eINSTANCE;

	private TypeFactory typeFactory;

	private Map<Component, List<Variable>> cellSegmentMap = new HashMap<Component, List<Variable>>();

	private GeppettoModelAccess access;
	
	private PopulateProjectionTypes populateProjectionTypes;
	private PopulateElectricalProjectionTypes populateElectricalProjectionTypes;
	private PopulateContinuousProjectionTypes populateContinuousProjectionTypes;
	
	public PopulateTypes(Map<String, Type> types, GeppettoModelAccess access)
	{
		super();
		this.types = types;
		this.typeFactory = new TypeFactory(types);
		this.access = access;
		
		populateProjectionTypes = new PopulateProjectionTypes(this, access);
		populateElectricalProjectionTypes = new PopulateElectricalProjectionTypes(this, access);
		populateContinuousProjectionTypes = new PopulateContinuousProjectionTypes(this, access);
	}

	/*
	 * Generic method to extract info from any component
	 */
	public CompositeType extractInfoFromComponent(Component component, String domainType) throws NumberFormatException, NeuroMLException, LEMSException, GeppettoVisitingException,
			ModelInterpreterException
	{
		// Create composite type depending on type of component and initialise it
		CompositeType compositeType = (CompositeType) typeFactory.getType((domainType == null) ? ResourcesDomainType.getValueByComponentType(component.getComponentType()) : domainType);
		NeuroMLModelInterpreterUtils.initialiseNodeFromComponent(compositeType, component);

		List<String> attributes = new ArrayList<String>();
		// Parameter types
		for(ParamValue pv : component.getParamValues())
		{
			if(component.hasAttribute(pv.getName()))
			{
				attributes.add(pv.getName());
				compositeType.getVariables().add(ModelInterpreterUtils.createParameterTypeVariable(pv.getName(), component.getStringValue(pv.getName()), this.access));
			}
		}

		// Text types
		for(Entry<String, String> entry : component.getTextParamMap().entrySet())
		{
			attributes.add(entry.getKey());
			compositeType.getVariables().add(ModelInterpreterUtils.createTextTypeVariable(entry.getKey(), entry.getValue(), this.access));
		}

		// Composite Type
		for(Entry<String, Component> entry : component.getRefComponents().entrySet())
		{
			Component refComponent = entry.getValue();
			attributes.add(refComponent.getID());
			if(!types.containsKey(refComponent.getDeclaredType() + refComponent.getID()))
			{
				types.put(refComponent.getDeclaredType() + refComponent.getID(), extractInfoFromComponent(refComponent, null));
			}
			Variable variable = variablesFactory.createVariable();
			NeuroMLModelInterpreterUtils.initialiseNodeFromComponent(variable, refComponent);
			variable.getTypes().add(types.get(refComponent.getDeclaredType() + refComponent.getID()));
			compositeType.getVariables().add(variable);
		}

		if(attributes.size() < component.getAttributes().size())
		{
			for(Attribute entry : component.getAttributes())
			{
				if(!attributes.contains(entry.getName()))
				{
					// component.getRelativeComponent("../pyramidals_48/37/pyr_4_sym")
					// component.getRelativeComponent(entry.getValue());
					// connection.getA().add(PointerUtility.getPointer(prePopulationVariable, prePopulationType, Integer.parseInt(preCellId)));

					// AQP For now: let's added as a metatype because I haven't found an easy way to extract the pointer
					compositeType.getVariables().add(ModelInterpreterUtils.createTextTypeVariable(entry.getName(), entry.getValue(), this.access));
				}
			}
		}

		// Extracting populations (this needs to be executed before extracting the projection otherwise we can't get the population)
		for(Component population : component.getChildrenAL("populations"))
		{
			if(!types.containsKey(population.getDeclaredType() + population.getID()))
			{
				createPopulationTypeVariable(population);
			}
			Variable variable = variablesFactory.createVariable();
			NeuroMLModelInterpreterUtils.initialiseNodeFromComponent(variable, population);
			variable.getTypes().add(types.get(population.getDeclaredType() + population.getID()));
			compositeType.getVariables().add(variable);
		}

		// Extracting projection and connections
		for(Component projection : component.getChildrenAL("projections"))
		{
			populateProjectionTypes.createConnectionTypeVariablesFromProjection(projection, compositeType);
		}
		for(Component projection : component.getChildrenAL("electricalProjection"))
		{
			populateElectricalProjectionTypes.createConnectionTypeVariablesFromProjection(projection, compositeType);
		}
		for(Component projection : component.getChildrenAL("continuousProjection"))
		{
			populateContinuousProjectionTypes.createConnectionTypeVariablesFromProjection(projection, compositeType);
		}
		
		
		// Extracting the rest of the child
		for(Component componentChild : component.getChildHM().values())
		{
			if(componentChild.getDeclaredType().equals(Resources.MORPHOLOGY.getId()))
			{
				createVisualTypeFromMorphology(component, compositeType, componentChild);
			}
			else if(componentChild.getDeclaredType().equals(Resources.ANNOTATION.getId()))
			{
				NeuroMLModelInterpreterUtils.createCompositeTypeFromAnnotation(compositeType, componentChild, access);
			}
			else if(componentChild.getDeclaredType().equals(Resources.NOTES.getId()))
			{
				compositeType.getVariables().add(ModelInterpreterUtils.createTextTypeVariable(Resources.NOTES.get(), componentChild.getAbout(), this.access));
			}
			else
			{
				// For the moment all the child are extracted as anonymous types
				CompositeType anonymousCompositeType = extractInfoFromComponent(componentChild, null);
				if(anonymousCompositeType != null)
				{
					Variable variable = variablesFactory.createVariable();
					NeuroMLModelInterpreterUtils.initialiseNodeFromComponent(variable, componentChild);
					variable.getAnonymousTypes().add(anonymousCompositeType);
					compositeType.getVariables().add(variable);
				}
			}
		}

		// Extracting the rest of the children
		for(Component componentChild : component.getStrictChildren())
		{
			if((!componentChild.getComponentType().isOrExtends(Resources.POPULATION.getId()) && !componentChild.getComponentType().isOrExtends(Resources.POPULATION_LIST.getId()))
					&& !componentChild.getComponentType().isOrExtends(Resources.PROJECTION.getId()) && !componentChild.getComponentType().isOrExtends(Resources.ELECTRICAL_PROJECTION.getId())
					&& !componentChild.getComponentType().isOrExtends(Resources.CONTINUOUS_PROJECTION.getId()))
			{
				// If it is not a population, a projection/connection or a morphology, let's deal with it in a generic way
				CompositeType anonymousCompositeType = extractInfoFromComponent(componentChild, null);
				if(anonymousCompositeType != null)
				{
					// For the moment all the children are extracted as anonymous types
					Variable variable = variablesFactory.createVariable();
					NeuroMLModelInterpreterUtils.initialiseNodeFromComponent(variable, componentChild);
					variable.getAnonymousTypes().add(anonymousCompositeType);
					compositeType.getVariables().add(variable);
				}
			}
		}

		// Exposures are the variables that can potentially be watched
		for(Exposure exposure : component.getComponentType().getExposures())
		{
			if(cellSegmentMap.containsKey(component) && cellSegmentMap.get(component).size() > 1 && (exposure.getName().equals("v") || exposure.getName().equals("spiking")))
			{
				if(!types.containsKey("compartment") || ((CompositeType) types.get("compartment")).getVariables().size() == 1)
				{

					if(!types.containsKey("compartment"))
					{
						CompositeType compartmentCompositeType = (CompositeType) typeFactory.getType(null);
						NeuroMLModelInterpreterUtils.initialiseNodeFromString(compartmentCompositeType, "compartment");
						types.put("compartment", compartmentCompositeType);
					}

					if(exposure.getName().equals("v") || exposure.getName().equals("spiking"))
					{
						CompositeType compartmentCompositeType = (CompositeType) types.get("compartment");
						compartmentCompositeType.getVariables().add(
								ModelInterpreterUtils.createExposureTypeVariable(exposure.getName(), Utils.getSIUnitInNeuroML(exposure.getDimension()).getSymbol(), this.access));

					}
				}
			}
			else
			{
				compositeType.getVariables().add(ModelInterpreterUtils.createExposureTypeVariable(exposure.getName(), Utils.getSIUnitInNeuroML(exposure.getDimension()).getSymbol(), this.access));
			}
		}

		if(cellSegmentMap.containsKey(component) && cellSegmentMap.get(component).size() > 1)
		{
			for(Variable compartment : cellSegmentMap.get(component))
			{
				Variable variable = variablesFactory.createVariable();
				variable.setName(Resources.getValueById(compartment.getName()));
				variable.setId(compartment.getId());
				variable.getTypes().add(types.get("compartment"));
				compositeType.getVariables().add(variable);
			}
		}

		return compositeType;

	}

	private void createVisualTypeFromMorphology(Component component, CompositeType compositeType, Component morphology) throws GeppettoVisitingException, LEMSException, NeuroMLException,
			ModelInterpreterException
	{
		if(!types.containsKey(morphology.getDeclaredType() + morphology.getParent().getID() + "_" + morphology.getID()))
		{
			ExtractVisualType extractVisualType = new ExtractVisualType(component, access);
			types.put(morphology.getDeclaredType() + morphology.getParent().getID() + "_" + morphology.getID(), extractVisualType.createTypeFromCellMorphology());

			cellSegmentMap.put(component, extractVisualType.getVisualObjectsSegments());
		}
		compositeType.setVisualType((VisualType) types.get(morphology.getDeclaredType() + morphology.getParent().getID() + "_" + morphology.getID()));
	}

	public void createPopulationTypeVariable(Component populationComponent) throws GeppettoVisitingException, LEMSException, NeuroMLException, NumberFormatException, ModelInterpreterException
	{
		Component refComponent = populationComponent.getRefComponents().get("component");
		if(!types.containsKey(refComponent.getDeclaredType() + refComponent.getID()))
		{
			types.put(refComponent.getDeclaredType() + refComponent.getID(), extractInfoFromComponent(refComponent, ResourcesDomainType.CELL.getId()));
		}
		CompositeType refCompositeType = (CompositeType) types.get(refComponent.getDeclaredType() + refComponent.getID());

		ArrayType arrayType = (ArrayType) typeFactory.getType(ResourcesDomainType.POPULATION.getId());
		NeuroMLModelInterpreterUtils.initialiseNodeFromComponent(arrayType, populationComponent);

		// If it is not of type cell, it won't have morphology and we can assume an sphere in the
		if(!refComponent.getComponentType().isOrExtends(Resources.CELL.getId()))
		{
			if(!types.containsKey("morphology_sphere"))
			{
				CompositeVisualType visualCompositeType = typesFactory.createCompositeVisualType();
				NeuroMLModelInterpreterUtils.initialiseNodeFromString(visualCompositeType, "morphology_sphere");

				Sphere sphere = valuesFactory.createSphere();
				sphere.setRadius(1.2d);
				Point point = valuesFactory.createPoint();
				point.setX(0);
				point.setY(0);
				point.setZ(0);
				sphere.setPosition(point);

				Variable variable = variablesFactory.createVariable();
				NeuroMLModelInterpreterUtils.initialiseNodeFromString(variable, refComponent.getID());
				variable.getTypes().add(access.getType(TypesPackage.Literals.VISUAL_TYPE));
				variable.getInitialValues().put(access.getType(TypesPackage.Literals.VISUAL_TYPE), sphere);

				visualCompositeType.getVariables().add(variable);

				types.put("morphology_sphere", visualCompositeType);
			}

			refCompositeType.setVisualType((VisualType) types.get("morphology_sphere"));

		}
		arrayType.setArrayType(refCompositeType);

		ArrayValue arrayValue = valuesFactory.createArrayValue();

		String populationType = populationComponent.getTypeName();
		// If it is not of type populationList we don't have to do anything in particular
		if(populationType != null && populationType.equals("populationList"))
		{

			int size = 0;
			for(Component populationChild : populationComponent.getAllChildren())
			{
				if(populationChild.getDeclaredType().equals("instance"))
				{
					Point point = null;
					for(Component instanceChild : populationChild.getAllChildren())
					{
						if(instanceChild.getDeclaredType().equals("location"))
						{
							point = valuesFactory.createPoint();
							point.setX(Double.parseDouble(instanceChild.getStringValue("x")));
							point.setY(Double.parseDouble(instanceChild.getStringValue("y")));
							point.setZ(Double.parseDouble(instanceChild.getStringValue("z")));
						}
					}

					ArrayElement arrayElement = valuesFactory.createArrayElement();
					arrayElement.setIndex(Integer.parseInt(populationChild.getID()));
					arrayElement.setPosition(point);
					arrayValue.getElements().add(arrayElement);

					size++;
				}
			}
			arrayType.setSize(size);
		}
		else
		{
			// If it has size attribute we read it otherwise we count the number of instances
			if(populationComponent.hasStringValue(Resources.SIZE.getId())) arrayType.setSize(Integer.parseInt(populationComponent.getStringValue(Resources.SIZE.getId())));
		}
		arrayType.setDefaultValue(arrayValue);
		types.put(Resources.POPULATION.getId() + populationComponent.getID(), arrayType);
	}

	public Map<ResourcesDomainType, List<Type>> getTypesMap()
	{
		return typeFactory.getTypesMap();
	}

	public TypeFactory getTypeFactory()
	{
		return typeFactory;
	}

	public Map<String, Type> getTypes()
	{
		return types;
	}
	
	

	
}
