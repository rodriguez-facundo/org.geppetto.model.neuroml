package org.geppetto.model.neuroml.modelInterpreterUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.ModelInterpreterException;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.neuroml.utils.ModelInterpreterUtils;
import org.geppetto.model.neuroml.utils.Resources;
import org.geppetto.model.neuroml.utils.ResourcesDomainType;
import org.geppetto.model.types.ArrayType;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.ConnectionType;
import org.geppetto.model.types.ImportType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.PointerUtility;
import org.geppetto.model.values.Connection;
import org.geppetto.model.values.Connectivity;
import org.geppetto.model.values.PhysicalQuantity;
import org.geppetto.model.values.Text;
import org.geppetto.model.values.Unit;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.values.VisualReference;
import org.geppetto.model.variables.Variable;
import org.lemsml.jlems.core.sim.ContentError;
import org.lemsml.jlems.core.sim.LEMSException;
import org.lemsml.jlems.core.type.Component;
import org.neuroml.model.BaseConnectionOldFormat;
import org.neuroml.model.Cell;
import org.neuroml.model.util.NeuroMLException;

public class PopulateProjectionTypes extends APopulateProjectionTypes
{

	public PopulateProjectionTypes(PopulateTypes populateTypes, GeppettoModelAccess access, GeppettoLibrary library)
	{
		super(populateTypes, access, library);
	}

	public Type resolveProjectionImportType(Component projection, ImportType importType) throws ModelInterpreterException
	{

		super.resolveProjectionImportType(projection, importType);

		// Add synapse variable to projection to projection type
		Component synapse = projection.getRefComponents().get(Resources.SYNAPSE.getId());
		Variable synapsesVariable = variablesFactory.createVariable();
		NeuroMLModelInterpreterUtils.initialiseNodeFromComponent(synapsesVariable, synapse);
		synapsesVariable.getTypes().add(populateTypes.getTypes().get(synapse.getDeclaredType() + synapse.getID()));
		projectionType.getVariables().add(synapsesVariable);

		try
		{

			// Iterate over all the children. Most of them are connections
			for(Component projectionChild : projection.getStrictChildren())
			{
				if(projectionChild.getComponentType().isOrExtends(Resources.CONNECTION.getId()) || projectionChild.getComponentType().isOrExtends(Resources.CONNECTION_WD.getId()))
				{
					projectionType.getVariables().add(extractConnection(projectionChild, prePopulationType, prePopulationVariable, postPopulationType, postPopulationVariable));
				}
				else
				{
					CompositeType anonymousCompositeType = populateTypes.extractInfoFromComponent(projectionChild);
					if(anonymousCompositeType != null)
					{
						Variable variable = variablesFactory.createVariable();
						NeuroMLModelInterpreterUtils.initialiseNodeFromComponent(variable, projectionChild);
						variable.getAnonymousTypes().add(anonymousCompositeType);
						projectionType.getVariables().add(variable);
					}
				}
			}
			
			// h5 file case, use networkhelper to get projection children
			int nConnections = populateTypes.getNetworkHelper().getNumberConnections(projection.getID());
			if (projection.getStrictChildren().size() < nConnections) {
				for (int i=0; i<nConnections; ++i) {
					BaseConnectionOldFormat connection = populateTypes.getNetworkHelper().getConnection(projection.getID(), i);
					projectionType.getVariables().add(extractConnection(connection, prePopulationType, prePopulationVariable, postPopulationType, postPopulationVariable));
				}
		    	}

			return projectionType;
		}
		catch(NeuroMLException | LEMSException | GeppettoVisitingException e)
		{
			throw new ModelInterpreterException(e);
		}

	}

	/**
	 * @param projectionChild
	 * @param prePopulationType
	 * @param prePopulationVariable
	 * @param postPopulationType
	 * @param postPopulationVariable
	 * @return
	 * @throws ModelInterpreterException
	 */
	private Variable extractConnection(Component projectionChild, ArrayType prePopulationType, Variable prePopulationVariable, ArrayType postPopulationType, Variable postPopulationVariable)
            throws ModelInterpreterException, GeppettoVisitingException
	{
		ConnectionType connectionType = (ConnectionType) populateTypes.getTypeFactory().getSuperType(ResourcesDomainType.CONNECTION);
                connectionType.getSuperType().add(this.geppettoModelAccess.getType(TypesPackage.Literals.CONNECTION_TYPE));
		Connection connection = valuesFactory.createConnection();
		connection.setConnectivity(Connectivity.DIRECTIONAL);
		String weight = null;
		String delay = null;
		try
		{
			String preCell = ModelInterpreterUtils.parseCellRefStringForCellNum(projectionChild.getAttributeValue("preCellId"));
			String postCell = ModelInterpreterUtils.parseCellRefStringForCellNum(projectionChild.getAttributeValue("postCellId"));
			String preSegmentId = null ;
			if(projectionChild.hasAttribute("preSegmentId")){
				preSegmentId=projectionChild.getAttributeValue("preSegmentId");
			}
			String preFractionAlong = null;
			if(projectionChild.hasAttribute("preFractionAlong")){
				preFractionAlong=projectionChild.getAttributeValue("preFractionAlong");
			}
			String postSegmentId = null;
			if(projectionChild.hasAttribute("postSegmentId")){
				postSegmentId=projectionChild.getAttributeValue("postSegmentId");
			}
			String postFractionAlong = null;
			if(projectionChild.hasAttribute("postFractionAlong")){
				postFractionAlong=projectionChild.getAttributeValue("postFractionAlong");
			}
			if(projectionChild.getTypeName().equals(ResourcesDomainType.CONNECTIONWD.getId())) {
				weight = projectionChild.getAttributeValue("weight");
				delay = projectionChild.getAttributeValue("delay");
			}
			if(preCell != null)
			{
				connection.setA(PointerUtility.getPointer(prePopulationVariable, prePopulationType, Integer.parseInt(preCell)));
				if(preSegmentId != null)
				{
					Cell neuroMLCell = this.populateTypes.getGeppettoCellTypesMap().get(prePopulationType.getArrayType());
					connection.getA().setPoint(NeuroMLModelInterpreterUtils.getPointAtFractionAlong(neuroMLCell, preSegmentId,preFractionAlong));
				}
			}
			if(postCell != null)
			{
				connection.setB(PointerUtility.getPointer(postPopulationVariable, postPopulationType, Integer.parseInt(postCell)));
				if(postSegmentId != null)
				{
					Cell neuroMLCell = this.populateTypes.getGeppettoCellTypesMap().get(postPopulationType.getArrayType());
					connection.getB().setPoint(NeuroMLModelInterpreterUtils.getPointAtFractionAlong(neuroMLCell,postSegmentId,postFractionAlong));
				}
			}
		}
		catch(ContentError | NumberFormatException | NeuroMLException e)
		{
			throw new ModelInterpreterException(e);
		}

		Variable variable = variablesFactory.createVariable();
		NeuroMLModelInterpreterUtils.initialiseNodeFromComponent(variable, projectionChild);
		variable.getTypes().add(connectionType);
		variable.getInitialValues().put(connectionType, connection);

		if(projectionChild.getTypeName().equals(ResourcesDomainType.CONNECTIONWD.getId())) {
			String regExp = "\\s*([0-9-]*\\.?[0-9]*[eE]?[-+]?[0-9]+)?\\s*(\\w*)";
	        Pattern pattern = Pattern.compile(regExp);
	        Matcher matcher = pattern.matcher(delay);
	
	        if(matcher.find())
	        {
	                PhysicalQuantity physicalQuantity = valuesFactory.createPhysicalQuantity();
	                Unit unit = valuesFactory.createUnit();
	                unit.setUnit(matcher.group(2));
	                physicalQuantity.setUnit(unit);
	                physicalQuantity.setValue(Float.parseFloat(matcher.group(1)));
	                variable.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.PARAMETER_TYPE), physicalQuantity);
	        }

            Text weightValue = valuesFactory.createText();
            weightValue.setText(weight);
            Variable weightVar = variablesFactory.createVariable();
            NeuroMLModelInterpreterUtils.initialiseNodeFromString(weightVar, "weight");
            variable.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE), weightValue);
		}

		return variable;

	}
	
	/**
	 * @param baseConnection
	 * @param prePopulationType
	 * @param prePopulationVariable
	 * @param postPopulationType
	 * @param postPopulationVariable
	 * @return
	 * @throws ModelInterpreterException
	 */
	private Variable extractConnection(BaseConnectionOldFormat baseConnection, ArrayType prePopulationType, Variable prePopulationVariable, ArrayType postPopulationType, Variable postPopulationVariable)
            throws ModelInterpreterException, GeppettoVisitingException
	{
		ConnectionType connectionType = (ConnectionType) populateTypes.getTypeFactory().getSuperType(ResourcesDomainType.CONNECTION);
                connectionType.getSuperType().add(this.geppettoModelAccess.getType(TypesPackage.Literals.CONNECTION_TYPE));
		Connection connection = valuesFactory.createConnection();
		connection.setConnectivity(Connectivity.DIRECTIONAL);

		try
		{
			String preCell = baseConnection.getPreCellId();
			String postCell = baseConnection.getPostCellId();
			String preSegmentId = Integer.toString(baseConnection.getPreSegmentId());
			String preFractionAlong = Float.toString(baseConnection.getPreFractionAlong());
			String postSegmentId = Integer.toString(baseConnection.getPostSegmentId());
			String postFractionAlong = Float.toString(baseConnection.getPostFractionAlong());

			if(preCell != null)
			{
				connection.setA(PointerUtility.getPointer(prePopulationVariable, prePopulationType, Integer.parseInt(preCell)));
				if(preSegmentId != null)
				{
					Cell neuroMLCell = this.populateTypes.getGeppettoCellTypesMap().get(prePopulationType.getArrayType());
					connection.getA().setPoint(NeuroMLModelInterpreterUtils.getPointAtFractionAlong(neuroMLCell, preSegmentId,preFractionAlong));
				}
			}
			if(postCell != null)
			{
				connection.setB(PointerUtility.getPointer(postPopulationVariable, postPopulationType, Integer.parseInt(postCell)));
				if(postSegmentId != null)
				{
					Cell neuroMLCell = this.populateTypes.getGeppettoCellTypesMap().get(postPopulationType.getArrayType());
					connection.getB().setPoint(NeuroMLModelInterpreterUtils.getPointAtFractionAlong(neuroMLCell,postSegmentId,postFractionAlong));
				}
			}
		}
		catch(NumberFormatException | NeuroMLException e)
		{
			throw new ModelInterpreterException(e);
		}

		Variable variable = variablesFactory.createVariable();
		NeuroMLModelInterpreterUtils.initialiseNodeFromString(variable, "id"+baseConnection.getId().toString());
		variable.getTypes().add(connectionType);
		variable.getInitialValues().put(connectionType, connection);
		return variable;

	}

}
