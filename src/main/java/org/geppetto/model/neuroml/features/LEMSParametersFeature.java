package org.geppetto.model.neuroml.features;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geppetto.core.features.ISetParameterFeature;
import org.geppetto.core.model.ModelInterpreterException;
import org.geppetto.core.services.GeppettoFeature;
import org.geppetto.model.DomainModel;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.VariableValue;
import org.geppetto.model.neuroml.utils.modeltree.PopulateModelTree;
import org.geppetto.model.util.PointerUtility;
import org.geppetto.model.values.PhysicalQuantity;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;

/**
 * Set a Lems Parameter (State Variable)
 * 
 * @author Adrian Quintana (adrian.perez@ucl.ac.uk)
 * 
 */
public class LEMSParametersFeature implements ISetParameterFeature{

	private static Log logger = LogFactory.getLog(LEMSParametersFeature.class);
	
	private PopulateModelTree populateModelTree = new PopulateModelTree();
	
	private DomainModel model;

	private GeppettoFeature type = GeppettoFeature.SET_PARAMETERS_FEATURE;
	
	private GeppettoLibrary library;

	public LEMSParametersFeature(PopulateModelTree populateModelTree, DomainModel model){
		this.populateModelTree = populateModelTree;
		this.model = model;
	}
	
	public LEMSParametersFeature(GeppettoLibrary library){
		this.library = library;
	}
	
	@Override
	public GeppettoFeature getType()
	{
		return type;
	}

	@Override
	public void setParameter(VariableValue variableValue) throws ModelInterpreterException
	{
		
		String propertyName = variableValue.getPointer().getElements().get(variableValue.getPointer().getElements().size()-1).getVariable().getId();
		
		PropertyAccessor myAccessor = PropertyAccessorFactory.forDirectFieldAccess(PointerUtility.getType(variableValue.getPointer()).getDomainModel());
		myAccessor.setPropertyValue(propertyName, ((PhysicalQuantity)variableValue.getValue()).getValue());
		
//		Map<String, ParameterSpecificationNode> modelParameters = this.populateModelTree.getParametersNode();
//
//		Set<String> paramValues = parameters.keySet();
//		Iterator<String> it = paramValues.iterator();
//		while(it.hasNext())
//		{
//			String newValue = it.next();
//			AValue value = new DoubleValue(Double.valueOf(parameters.get(newValue)));
//			ParameterSpecificationNode node = modelParameters.get(newValue);
//			node.getValue().setValue(value);
//			node.setModified(true);
//
//			// retrieve NeuroML object instance associated with param node
//			Object instance = this.populateModelTree.getParametersNodeToObjectsMap().get(node);
//			// retrieve setter method from instance associated with param node
//			Method method = (Method)this.populateModelTree.getParametersNodeToMethodsMap().get(node);
//			try
//			{
//				// invoke setter method passing instance and new value
//				String valueWithUnit=node.getValue().getValue().toString() +node.getValue().getUnit();
//				method.invoke(instance, valueWithUnit);
//			}
//			catch(Exception e)
//			{
//				throw new ModelInterpreterException(e);
//			}
//
//			try
//			{
//				// Change the parameter value also in the LEMS model
//				Lems lems = (Lems) model.getModel(ServicesRegistry.getModelFormat("LEMS"));
//				lems.setResolveModeLoose();
//				lems.deduplicate();
//				lems.resolve();
//				lems.evaluateStatic();
//				Component comp = LEMSAccessUtility.findLEMSComponent(lems.getComponents().getContents(), ((Base) instance).getId());
//				//unspeakable things happening, going from a method name to parameter name
//				String paramName = Character.toLowerCase(method.getName().charAt(3))+method.getName().substring(4);
//				ParamValue lemsParam = comp.getParamValue(paramName); 
//				String valueWithUnit=node.getValue().getValue().toString() +node.getValue().getUnit();
//				DimensionalQuantity dq = QuantityReader.parseValue(valueWithUnit, lems.getUnits());
//				lemsParam.setDoubleValue(dq.getDoubleValue());
//				logger.info("The value of the LEMS parameter "+paramName+" has changed, new value is "+valueWithUnit);
//			}
//			catch(ContentError e)
//			{
//				throw new ModelInterpreterException(e);
//			}
//			catch(ParseError e)
//			{
//				throw new ModelInterpreterException(e);
//			}
//
//		}
	}
}
