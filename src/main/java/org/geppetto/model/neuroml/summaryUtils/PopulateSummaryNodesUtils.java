/*******************************************************************************
 * The MIT License (MIT)
 * 
 * Copyright (c) 2011 - 2015 OpenWorm.
 * http://openworm.org
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the MIT License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/MIT
 *
 * Contributors:
 *     	OpenWorm - http://openworm.org/people.html
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights 
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell 
 * copies of the Software, and to permit persons to whom the Software is 
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR 
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE 
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/

package org.geppetto.model.neuroml.summaryUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.common.util.EList;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.ModelInterpreterException;
import org.geppetto.model.neuroml.utils.ModelInterpreterUtils;
import org.geppetto.model.neuroml.utils.Resources;
import org.geppetto.model.neuroml.utils.ResourcesDomainType;
import org.geppetto.model.types.ArrayType;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.CompositeVisualType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.types.VisualType;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.Argument;
import org.geppetto.model.values.Dynamics;
import org.geppetto.model.values.Expression;
import org.geppetto.model.values.Function;
import org.geppetto.model.values.FunctionPlot;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Text;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.values.VisualGroup;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;
import org.lemsml.jlems.core.sim.LEMSException;
import org.lemsml.jlems.core.type.Component;
import org.neuroml.export.info.model.ChannelInfoExtractor;
import org.neuroml.export.info.model.ExpressionNode;
import org.neuroml.export.info.model.InfoNode;
import org.neuroml.export.info.model.PlotMetadataNode;
import org.neuroml.model.Cell;
import org.neuroml.model.ExpOneSynapse;
import org.neuroml.model.ExpTwoSynapse;
import org.neuroml.model.GateHHInstantaneous;
import org.neuroml.model.GateHHRates;
import org.neuroml.model.GateHHRatesInf;
import org.neuroml.model.GateHHRatesTau;
import org.neuroml.model.GateHHRatesTauInf;
import org.neuroml.model.GateHHTauInf;
import org.neuroml.model.GateHHUndetermined;
import org.neuroml.model.IonChannel;
import org.neuroml.model.IonChannelHH;
import org.neuroml.model.Izhikevich2007Cell;
import org.neuroml.model.IzhikevichCell;
import org.neuroml.model.NeuroMLDocument;
import org.neuroml.model.PulseGenerator;
import org.neuroml.model.Standalone;
import org.neuroml.model.util.NeuroMLException;

/**
 * Populates the Model Tree of Aspect
 * 
 * @author Adrian Quintana (adrian.perez@ucl.ac.uk)
 * 
 */

public class PopulateSummaryNodesUtils
{
	private static Log logger = LogFactory.getLog(PopulateSummaryNodesUtils.class);
	private static String NOTES = "Notes";
	TypesFactory typeFactory = TypesFactory.eINSTANCE;
	VariablesFactory variablesFactory = VariablesFactory.eINSTANCE;
	ValuesFactory valuesFactory = ValuesFactory.eINSTANCE;

	GeppettoModelAccess access;
	Map<String, List<Type>> typesMap;
	Map<String, List<Variable>> plottableVariables = new HashMap<String, List<Variable>>();

	Type type;

	URL url;
	private NeuroMLDocument neuroMLDocument;
    
    boolean verbose = false;

	public PopulateSummaryNodesUtils(Map<String, List<Type>> typesMap, Type type, URL url, GeppettoModelAccess access, NeuroMLDocument neuroMLDocument)
	{
		this.access = access;
		this.typesMap = typesMap;
		this.url = url;
		this.type = type;
		this.neuroMLDocument = neuroMLDocument;
	}

	/**
	 * Creates all HTML variables for objects in maps.
	 */
	public void createHTMLVariables() throws ModelInterpreterException, GeppettoVisitingException, NeuroMLException, LEMSException
	{
		this.createCellsHTMLVariable();
		this.createSynapsesHTMLVariable();
		this.createChannelsHTMLVariable();
		this.createInputsHTMLVariable();
	}

	/**
	 * Creates general Model description
	 * 
	 * @return
	 * @throws ModelInterpreterException
	 * @throws GeppettoVisitingException
	 * @throws NeuroMLException
	 * @throws LEMSException
	 */
	public Variable getDescriptionNode() throws ModelInterpreterException, GeppettoVisitingException, NeuroMLException, LEMSException
	{

		List<Type> networkComponents = typesMap.containsKey(ResourcesDomainType.NETWORK.get()) ? typesMap.get(ResourcesDomainType.NETWORK.get()) : null;
		List<Type> populationComponents = typesMap.containsKey(ResourcesDomainType.POPULATION.get()) ? typesMap.get(ResourcesDomainType.POPULATION.get()) : null;
		List<Type> cellComponents = typesMap.containsKey(ResourcesDomainType.CELL.get()) ? typesMap.get(ResourcesDomainType.CELL.get()) : null;
		List<Type> ionChannelComponents = typesMap.containsKey(ResourcesDomainType.IONCHANNEL.get()) ? typesMap.get(ResourcesDomainType.IONCHANNEL.get()) : null;
		List<Type> synapseComponents = typesMap.containsKey(ResourcesDomainType.SYNAPSE.get()) ? typesMap.get(ResourcesDomainType.SYNAPSE.get()) : null;
		List<Type> pulseGeneratorComponents = typesMap.containsKey(ResourcesDomainType.PULSEGENERATOR.get()) ? typesMap.get(ResourcesDomainType.PULSEGENERATOR.get()) : null;

		StringBuilder modelDescription = new StringBuilder();

		if(networkComponents != null && networkComponents.size() > 0)
		{
            modelDescription.append("Network: ");
            for (Type network : networkComponents)
            {
                modelDescription.append("<a href=\"#\" instancePath=\"Model.neuroml." + network.getId() + "\">" + network.getName() + "</a><br/><br/>\n");
                List<Variable> notesComponents = new ArrayList<Variable>();
                EList<Variable> netVariables = ((CompositeType) network).getVariables();
                for (Variable v : netVariables)
                {
                    if (v.getId().equals(NOTES))
                    {
                        notesComponents.add(v);
                    }
                }
                for (Variable note : notesComponents)
                {
                    Text about = (Text) note.getInitialValues().get(access.getType(TypesPackage.Literals.TEXT_TYPE));
                    modelDescription.append("<b>Description</b><br/>\n<p instancePath=\"Model.neuroml." + note.getId() + "\">" + formatDescription(about.getText()) + "</p>\n ");
                }
            }


		}
		modelDescription.append("<a target=\"_blank\" href=\"" + url.toString() + "\"></i>View NeuroML 2 source file</i></a><br/><br/>\n");

		if(populationComponents != null && populationComponents.size() > 0)
		{
			modelDescription.append("<b>Populations</b><br/>\n");
			for(Type population : populationComponents)
			{
				modelDescription.append("" + population.getName() + ": ");
				// get proper name of population cell with brackets and index # of population
				String name = ((ArrayType) population).getArrayType().getId().trim() + "." + population.getId().trim() + "[" + populationComponents.indexOf(population) + "]";
				modelDescription.append("<a href=\"#\" instancePath=\"Model.neuroml." + name + "\">" + ((ArrayType) population).getSize() + " cells of type " + ((ArrayType) population).getArrayType().getName()
						+ "</a><br/>\n");
			}
			modelDescription.append("<br/>\n");
		}

		if(cellComponents != null && cellComponents.size() > 0)
		{
			modelDescription.append("<b>Cells</b><br/>  \n");
			for(Type cell : cellComponents)
			{
				modelDescription.append("<a href=\"#\" instancePath=\"Model.neuroml." + cell.getId() + "\">" + cell.getName() + "</a> | \n");
			}
			modelDescription.append("<br/><br/>\n");
		}

		if(ionChannelComponents != null && ionChannelComponents.size() > 0)
		{
			modelDescription.append("<b>Ion channels</b><br/>\n");
			for(Type ionChannel : ionChannelComponents)
			{

				modelDescription.append("<a href=\"#\" instancePath=\"Model.neuroml." + ionChannel.getId() + "\">" + ionChannel.getName() + "</a> | ");

				// Add expresion nodes from the export library for the gate rates
				addExpresionNodes((CompositeType) ionChannel);
			}
			modelDescription.append("<br/><br/>\n");
		}

		if(synapseComponents != null && synapseComponents.size() > 0)
		{
			modelDescription.append("<b>Synapses</b><br/>\n");
			for(Type synapse : synapseComponents)
			{
				modelDescription.append("<a href=\"#\" instancePath=\"Model.neuroml." + synapse.getId() + "\">" + synapse.getName() + "</a> | ");
			}
			modelDescription.append("<br/><br/>\n");
		}

		if(pulseGeneratorComponents != null && pulseGeneratorComponents.size() > 0)
		{
			// FIXME: Pulse generator? InputList? ExplicitList?
			modelDescription.append("<b>Inputs</b><br/>\n");
			for(Type pulseGenerator : pulseGeneratorComponents)
			{
				modelDescription.append("<a href=\"#\" instancePath=\"Model.neuroml." + pulseGenerator.getId() + "\">" + pulseGenerator.getName() + "</a> | ");
			}
			modelDescription.append("<br/>\n");
		}

		// If there is nothing at least show a link to open the whole model in a tree visualiser
		if((networkComponents == null || networkComponents.size() == 0) && (populationComponents == null || populationComponents.size() == 0) && (cellComponents == null || cellComponents.size() == 0)
				&& (synapseComponents == null || synapseComponents.size() == 0) && (pulseGeneratorComponents == null || pulseGeneratorComponents.size() == 0))
		{
			modelDescription.insert(0, "Description: <a href=\"#\" instancePath=\"Model.neuroml." + type.getId() + "\">" + type.getName() + "</a><br/><br/>\n");
		}

		HTML html = valuesFactory.createHTML();
		html.setHtml(modelDescription.toString());
                    
        if (verbose) System.out.println("=========== Model ===========\n"+modelDescription.toString());

		Variable descriptionVariable = variablesFactory.createVariable();
		descriptionVariable.setId(Resources.MODEL_DESCRIPTION.getId());
		descriptionVariable.setName(Resources.MODEL_DESCRIPTION.get());
		descriptionVariable.getTypes().add(access.getType(TypesPackage.Literals.HTML_TYPE));
		descriptionVariable.getInitialValues().put(access.getType(TypesPackage.Literals.HTML_TYPE), html);

		return descriptionVariable;
	}

	/**
	 * Create Variable with HTML value for a Cell
	 * 
	 * @param cell
	 *            - Cell used to create this html element
	 * @return
	 * @throws ModelInterpreterException
	 * @throws GeppettoVisitingException
	 * @throws NeuroMLException
	 * @throws LEMSException
	 */
	private void createCellsHTMLVariable() throws ModelInterpreterException, GeppettoVisitingException, NeuroMLException, LEMSException
	{
		try
		{
			List<Type> cellComponents = typesMap.containsKey(ResourcesDomainType.CELL.get()) ? typesMap.get(ResourcesDomainType.CELL.get()) : null;

			if(cellComponents != null && cellComponents.size() > 0)
			{
				for(Type cell : cellComponents)
				{
					List<Variable> notesComponents = new ArrayList<Variable>();
					List<Type> ionChannelComponents = typesMap.containsKey(ResourcesDomainType.IONCHANNEL.get()) ? typesMap.get(ResourcesDomainType.IONCHANNEL.get()) : null;
					List<Type> synapseComponents = typesMap.containsKey(ResourcesDomainType.SYNAPSE.get()) ? typesMap.get(ResourcesDomainType.SYNAPSE.get()) : null;
					List<Type> pulseGeneratorComponents = typesMap.containsKey(ResourcesDomainType.PULSEGENERATOR.get()) ? typesMap.get(ResourcesDomainType.PULSEGENERATOR.get()) : null;

			        EList<Variable> cellVariables = ((CompositeType) cell).getVariables();
                    for (Variable v : cellVariables)
                    {
                        if (v.getId().equals(NOTES))
                        {
                            notesComponents.add(v);
                        }
                    }

                    StringBuilder htmlText0 = new StringBuilder();
                    htmlText0.append("<b>Cell: </b> <a href=\"#\" instancePath=\"Model.neuroml." + cell.getId() + "\">" + cell.getId() + "</a><br/><br/>\n");

                    if (notesComponents != null && notesComponents.size() > 0)
                    {

                        htmlText0.append("<b>Description</b><br/>\n");
                        for (Variable note : notesComponents)
                        {
                            Text about = (Text) note.getInitialValues().get(access.getType(TypesPackage.Literals.TEXT_TYPE));
                            htmlText0.append("<p instancePath=\"Model.neuroml." + note.getId() + "\">" + formatDescription(about.getText()) + "</p>\n ");
                        }
                        htmlText0.append("<br/>\n");
                    }

                    Variable htmlVariable0 = variablesFactory.createVariable();
                    htmlVariable0.setId(Resources.NOTES.getId());
                    htmlVariable0.setName(Resources.NOTES.get());

                    // TODO: replace this hard coding!!
                    for (Izhikevich2007Cell c : neuroMLDocument.getIzhikevich2007Cell())
                    {
                        if (c.getId().equals(cell.getId()))
                        {
                            htmlText0.append("a: " + c.getA() + "<br/>\n");
                            htmlText0.append("b: " + c.getB() + "<br/>\n");
                            htmlText0.append("c: " + c.getC() + "<br/>\n");
                            htmlText0.append("d: " + c.getD() + "<br/>\n");
                            htmlText0.append("k: " + c.getK() + "<br/>\n");
                            htmlText0.append("v0: " + c.getV0() + "<br/>\n");
                            htmlText0.append("v peak: " + c.getVpeak() + "<br/>\n");
                            htmlText0.append("v reset: " + c.getVr() + "<br/>\n");
                            htmlText0.append("v threshold: " + c.getVt() + "<br/>\n");
                        }
                    }
                    for (IzhikevichCell c : neuroMLDocument.getIzhikevichCell())
                    {
                        if (c.getId().equals(cell.getId()))
                        {
                            htmlText0.append("a: " + c.getA() + "<br/>\n");
                            htmlText0.append("b: " + c.getB() + "<br/>\n");
                            htmlText0.append("c: " + c.getC() + "<br/>\n");
                            htmlText0.append("d: " + c.getD() + "<br/>\n");
                            htmlText0.append("v0: " + c.getV0() + "<br/>\n");
                            htmlText0.append("v threshold: " + c.getThresh() + "<br/>\n");
                        }
                    }
                    for (Cell c : neuroMLDocument.getCell())
                    {
                        if (c.getId().equals(cell.getId()))
                        {
                            htmlText0.append("Number of segments: " + c.getMorphology().getSegment().size() + "<br/>\n");
                            htmlText0.append("Number of segment groups: " + c.getMorphology().getSegmentGroup().size() + "<br/><br/>\n");
                        }
                    }

                    // Create HTML Value object and set HTML text
                    HTML html0 = valuesFactory.createHTML();
                    if (verbose)
                    {
                        System.out.println("========== Cell ============\n" + htmlText0.toString());
                    }
                    html0.setHtml(htmlText0.toString());
                    htmlVariable0.getTypes().add(access.getType(TypesPackage.Literals.HTML_TYPE));
                    htmlVariable0.getInitialValues().put(access.getType(TypesPackage.Literals.HTML_TYPE), html0);

                    ((CompositeType) cell).getVariables().add(htmlVariable0);
                    

					if(ionChannelComponents != null && ionChannelComponents.size() > 0)
					{
						StringBuilder htmlText = new StringBuilder();

						htmlText.append("<b>Ion channels</b><br/>");
						for(Type ionChannel : ionChannelComponents)
						{
							htmlText.append("<a href=\"#\" instancePath=\"Model.neuroml." + ionChannel.getId() + "\">" + ionChannel.getName() + "</a> | ");
						}
						htmlText.append("<br/><br/>");

						Variable htmlVariable = variablesFactory.createVariable();
						htmlVariable.setId(Resources.ION_CHANNEL.getId());
						htmlVariable.setName(Resources.ION_CHANNEL.get());

						// Create HTML Value object and set HTML text
						HTML html = valuesFactory.createHTML();
						html.setHtml(htmlText.toString());
						htmlVariable.getTypes().add(access.getType(TypesPackage.Literals.HTML_TYPE));
						htmlVariable.getInitialValues().put(access.getType(TypesPackage.Literals.HTML_TYPE), html);

						((CompositeType) cell).getVariables().add(htmlVariable);
					}

					if(synapseComponents != null && synapseComponents.size() > 0)
					{
						StringBuilder htmlText = new StringBuilder();

						htmlText.append("<b>Synapses</b><br/>");
						for(Type synapse : synapseComponents)
						{
							htmlText.append("<a href=\"#\" instancePath=\"Model.neuroml." + synapse.getId() + "\">" + synapse.getName() + "</a> | ");
						}
						htmlText.append("<br/><br/>");

						Variable htmlVariable = variablesFactory.createVariable();
						htmlVariable.setId(Resources.SYNAPSE.getId());
						htmlVariable.setName(Resources.SYNAPSE.get());

						// Create HTML Value object and set HTML text
						HTML html = valuesFactory.createHTML();
						html.setHtml(htmlText.toString());
						htmlVariable.getTypes().add(access.getType(TypesPackage.Literals.HTML_TYPE));
						htmlVariable.getInitialValues().put(access.getType(TypesPackage.Literals.HTML_TYPE), html);

						((CompositeType) cell).getVariables().add(htmlVariable);
					}

					// Add Visual Group to model cell description
					VisualType visualType = cell.getVisualType();
					if(visualType != null)
					{
						List<VisualGroup> visualGroups = ((CompositeVisualType) visualType).getVisualGroups();
						if(visualGroups != null && visualGroups.size() > 0)
						{
							StringBuilder htmlText = new StringBuilder();

							htmlText.append("<b>Click to apply colouring to the cell morphology</b><br/>");
							for(VisualGroup visualGroup : visualGroups)
							{
								htmlText.append("<a href=\"#\" type=\"visual\" instancePath=\"Model.neuroml." + visualType.getId() + "." + visualGroup.getId() + "\">Highlight " + visualGroup.getName()
										+ "</a> | ");
							}
							htmlText.append("<br/><br/>");

							Variable htmlVariable = variablesFactory.createVariable();
							htmlVariable.setId(visualType.getId());
							htmlVariable.setName(visualType.getName());

							// Create HTML Value object and set HTML text
							HTML html = valuesFactory.createHTML();
							html.setHtml(htmlText.toString());
							htmlVariable.getTypes().add(access.getType(TypesPackage.Literals.HTML_TYPE));
							htmlVariable.getInitialValues().put(access.getType(TypesPackage.Literals.HTML_TYPE), html);

							((CompositeType) cell).getVariables().add(htmlVariable);
						}
					}

					if(pulseGeneratorComponents != null && pulseGeneratorComponents.size() > 0)
					{
						StringBuilder htmlText = new StringBuilder();
						// FIXME: Pulse generator? InputList? ExplicitList?
						htmlText.append("<b>Inputs</b><br/>");
						for(Type pulseGenerator : pulseGeneratorComponents)
						{
							htmlText.append("<a href=\"#\" instancePath=\"Model.neuroml." + pulseGenerator.getId() + "\">" + pulseGenerator.getName() + "</a> ");
						}
						htmlText.append("<br/>");

						Variable htmlVariable = variablesFactory.createVariable();
						htmlVariable.setId(Resources.PULSE_GENERATOR.getId());
						htmlVariable.setName(Resources.PULSE_GENERATOR.get());

						// Create HTML Value object and set HTML text
						HTML html = valuesFactory.createHTML();
						html.setHtml(htmlText.toString());
						htmlVariable.getTypes().add(access.getType(TypesPackage.Literals.HTML_TYPE));
						htmlVariable.getInitialValues().put(access.getType(TypesPackage.Literals.HTML_TYPE), html);

						((CompositeType) cell).getVariables().add(htmlVariable);
					}
				}
			}
		}
		catch(Exception e)
		{
			throw new ModelInterpreterException(e);
		}
	}
    
    private String formatDescription(String desc) 
    {
        desc = desc.replaceAll("\n", "<br/>\n");
        return parseForHyperlinks(desc);
    }
    
    private static String replaceToken(String line, String oldToken, String newToken, int fromIndex)
    {
        StringBuffer sb = new StringBuffer(line);
        sb.replace(line.indexOf(oldToken, fromIndex), line.indexOf(oldToken, fromIndex)+oldToken.length(), newToken);
        return sb.toString();
    }
    
    private static String parseForHyperlinks(String text)
    {
        String[] prefixes ={"http://","https://"};
        int checkpoint = 0;
        
        for (String prefix: prefixes) 
        {
            while(text.indexOf(prefix, checkpoint)>=0)
            {
                int start = text.indexOf(prefix, checkpoint);
                int end = text.length();
                if (text.indexOf(" ", start)>0)
                    end = text.indexOf(" ", start);
                if (text.indexOf("\n", start)>0)
                    end = Math.min(end,text.indexOf("\n", start));
                if (text.indexOf(")", start)>0)
                    end = Math.min(end,text.indexOf(")", start));

                String url = text.substring(start, end);
                if (url.endsWith("."))
                {
                    url=url.substring(0,url.length()-1);
                }

                String link = "<a href=\""+url+"\"  target=\"_blank\">"+url+"</a>";

                text = replaceToken(text, url, link, start);

                checkpoint = start+link.length();
            }
        }
        return text;
    }
    
    private void extractDescription(Type t, StringBuilder htmlText) throws GeppettoVisitingException
    {
        List<Variable> notesComponents = new ArrayList<Variable>();

        EList<Variable> channelVariables = ((CompositeType) t).getVariables();
        for(Variable v : channelVariables)
        {
            if(v.getId().equals(NOTES))
            {
                notesComponents.add(v);
            }
        }
        if(notesComponents.size() > 0)
        {
            htmlText.append("<b>Description</b><br/>\n");
            for(Variable note : notesComponents)
            {
                Text about = (Text) note.getInitialValues().get(access.getType(TypesPackage.Literals.TEXT_TYPE));
                htmlText.append("<p instancePath=\"Model.neuroml." + note.getId() + "\">" + formatDescription(about.getText()) + "</p> ");
            }
            htmlText.append("<br/>\n");
        }
    }

	private void createChannelsHTMLVariable() throws ModelInterpreterException, GeppettoVisitingException, NeuroMLException, LEMSException
	{
		List<Type> ionChannelComponents = typesMap.containsKey(ResourcesDomainType.IONCHANNEL.get()) ? typesMap.get(ResourcesDomainType.IONCHANNEL.get()) : null;

		if(ionChannelComponents != null && ionChannelComponents.size() > 0)
		{
			for(Type ionChannel : ionChannelComponents)
			{
				

				StringBuilder htmlText = new StringBuilder();
                
			    htmlText.append("<b>Ion channel: </b> <a href=\"#\" instancePath=\"Model.neuroml." + ionChannel.getId() + "\">"+ionChannel.getId()+"</a><br/><br/>\n");

                extractDescription(ionChannel, htmlText);
				
                Component component = ((Component) ionChannel.getDomainModel().getDomainModel());
                IonChannel chan = (IonChannel)getNeuroMLIonChannel(component);
				
                if (chan!=null) {
                    htmlText.append("<b>Ion:</b> <a href=\"#\">"+(chan.getSpecies()!=null ? chan.getSpecies() : "Non specific")+"</a><br/><br/>\n");
                    htmlText.append("<b>Conductance:</b> <a href=\"#\">"+createIonChannelExpression(chan)+"</a><br/><br/>\n");
                }

				// Adds plot activation variables
				List<Variable> variables = this.plottableVariables.get(ionChannel.getName());
				if(variables != null)
				{
					htmlText.append("<b>Plot activation variables</b><br/>\n");
					for(Variable v : variables)
					{
						String[] split = v.getPath().split("\\.");
						String shortLabel = v.getPath();
						String info = v.getPath();
						if(split.length > 5)
						{
							shortLabel = split[1] + "." + split[2] + "..." + split[split.length - 1];
                            info = "Gate: " + split[2] + " "+ split[split.length - 1].replace("_", " ");
                            info+= (info.indexOf("forward")>0 ? ", alpha<sub>" + split[2] + "</sub>":", beta<sub>" + split[2] + "</sub>");
						}
						htmlText.append("<a href=\"#\" type=\"variable\" instancePath=\"Model." + v.getPath() + "\">" + info + "</a><br/>\n");
					}
				}
				Variable htmlVariable = variablesFactory.createVariable();
				htmlVariable.setId(ionChannel.getId());
				htmlVariable.setName(ionChannel.getName());

				HTML html = valuesFactory.createHTML();
                if (verbose) System.out.println("======= Channel ===============\n"+htmlText.toString());
				html.setHtml(htmlText.toString());
				htmlVariable.getTypes().add(access.getType(TypesPackage.Literals.HTML_TYPE));
				htmlVariable.getInitialValues().put(access.getType(TypesPackage.Literals.HTML_TYPE), html);
				((CompositeType) ionChannel).getVariables().add(htmlVariable);
			}
		}
	}
    
    // Temp method before LEMS2...
    private String createIonChannelExpression(IonChannel chan) 
    {
		StringBuilder htmlText = new StringBuilder();
		StringBuilder postText = new StringBuilder();
        htmlText.append("G<sub>"+chan.getId()+"</sub>(v,t) = G<sub>max</sub> ");
        //neuroMLDocument.
        //ArrayList<String> gates = new ArrayList<>();
        for (GateHHUndetermined g: chan.getGate())
        {
            htmlText.append(" * "+g.getId()+"(v,t)"+( g.getInstances()!=1 ? ("<sup>"+g.getInstances()+"</sup>")  : ""));
            //postText.append("  d"+g.getId()+"/dt = alpha<sub>"+g.getId()+"</sub>(v) * (1 - "+g.getId()+") + beta<sub>"+g.getId()+"</sub>(v) * "+g.getId()+"");
        }
        for (GateHHInstantaneous g: chan.getGateHHInstantaneous()) htmlText.append(" * "+g.getId()+"(v,t)"+( g.getInstances()!=1 ? ("<sup>"+g.getInstances()+"</sup>")  : ""));
        
        for (GateHHRates g: chan.getGateHHrates())
        {
            htmlText.append(" * "+g.getId()+"(v,t)"+( g.getInstances()!=1 ? ("<sup>"+g.getInstances()+"</sup>")  : ""));
            //postText.append("d"+g.getId()+"/dt = alpha(v) * (1 - "+g.getId()+") + beta(v) * "+g.getId()+"");
        }

        for (GateHHRatesInf g: chan.getGateHHratesInf()) htmlText.append(" * "+g.getId()+"(v,t)"+( g.getInstances()!=1 ? ("<sup>"+g.getInstances()+"</sup>")  : ""));
        for (GateHHRatesTau g: chan.getGateHHratesTau()) htmlText.append(" * "+g.getId()+"(v,t)"+( g.getInstances()!=1 ? ("<sup>"+g.getInstances()+"</sup>")  : ""));
        for (GateHHRatesTauInf g: chan.getGateHHratesTauInf()) htmlText.append(" * "+g.getId()+"(v,t)"+( g.getInstances()!=1 ? ("<sup>"+g.getInstances()+"</sup>")  : ""));
        for (GateHHTauInf g: chan.getGateHHtauInf()) htmlText.append(" * "+g.getId()+"(v,t)"+( g.getInstances()!=1 ? ("<sup>"+g.getInstances()+"</sup>")  : ""));
        
        return htmlText.toString()+"<br/>\n"+postText.toString();
    }

	private void createSynapsesHTMLVariable() throws ModelInterpreterException, GeppettoVisitingException, NeuroMLException, LEMSException
	{
		List<Type> synapseComponents = typesMap.containsKey(ResourcesDomainType.SYNAPSE.get()) ? typesMap.get(ResourcesDomainType.SYNAPSE.get()) : null;

		if(synapseComponents != null && synapseComponents.size() > 0)
		{
			for(Type synapse : synapseComponents)
			{
				StringBuilder htmlText = new StringBuilder();
                
				Variable htmlVariable = variablesFactory.createVariable();
				htmlVariable.setId(synapse.getId());
				htmlVariable.setName(synapse.getName());
            
			    htmlText.append("<b>Synapse: </b> <a href=\"#\" instancePath=\"Model.neuroml." + synapse.getId() + "\">"+synapse.getId()+"</a><br/><br/>\n");
                
                extractDescription(synapse, htmlText);

				// Create HTML Value object and set HTML text
				HTML html = valuesFactory.createHTML();
                //BaseConductanceBasedSynapse syn = null;
                for (ExpOneSynapse syn : neuroMLDocument.getExpOneSynapse()){
                    if (syn.getId().equals(synapse.getId()))
                    {
                        htmlText.append("Base conductance: "+syn.getGbase()+"<br/>\n");
                        htmlText.append("Decay time: "+syn.getTauDecay()+"<br/>\n");
                        htmlText.append("Reversal potential: "+syn.getErev()+"<br/>\n");
                    }
                }
                for (ExpTwoSynapse syn : neuroMLDocument.getExpTwoSynapse()){
                    if (syn.getId().equals(synapse.getId()))
                    {
                        htmlText.append("Base conductance: "+syn.getGbase()+"<br/>\n");
                        htmlText.append("Rise time: "+syn.getTauRise()+"<br/>\n");
                        htmlText.append("Decay time: "+syn.getTauDecay()+"<br/>\n");
                        htmlText.append("Reversal potential: "+syn.getErev()+"<br/>\n");
                    }
                }
				html.setHtml(htmlText.toString());
                if (verbose) System.out.println("======= Synapse ===============\n"+htmlText.toString());
				htmlVariable.getTypes().add(access.getType(TypesPackage.Literals.HTML_TYPE));
				htmlVariable.getInitialValues().put(access.getType(TypesPackage.Literals.HTML_TYPE), html);
				((CompositeType) synapse).getVariables().add(htmlVariable);
			}
		}
	}

	private void createInputsHTMLVariable() throws ModelInterpreterException, GeppettoVisitingException, NeuroMLException, LEMSException
	{
		List<Type> pulseGeneratorComponents = typesMap.containsKey(ResourcesDomainType.PULSEGENERATOR.get()) ? typesMap.get(ResourcesDomainType.PULSEGENERATOR.get()) : null;

		if(pulseGeneratorComponents != null && pulseGeneratorComponents.size() > 0)
		{
			for(Type pulseGenerator : pulseGeneratorComponents)
			{
                PulseGenerator pg = null;
                for (PulseGenerator pg0 : neuroMLDocument.getPulseGenerator()){
                    if (pg0.getId().equals(pulseGenerator.getId()))
                        pg=pg0;
                }
                
				StringBuilder htmlText = new StringBuilder();

				Variable htmlVariable = variablesFactory.createVariable();
				htmlVariable.setId(pulseGenerator.getId());
				htmlVariable.setName(pulseGenerator.getName());

				// Create HTML Value object and set HTML text
				HTML html = valuesFactory.createHTML();
				htmlText.append("<a href=\"#\" instancePath=\"Model.neuroml." + pulseGenerator.getId() + "\">" + pulseGenerator.getName() + "</a> ");
				htmlText.append("<br/><br/>\n");
                
                htmlText.append("Delay: "+pg.getDelay()+"<br/>\n");
                htmlText.append("Duration: "+pg.getDuration()+"<br/>\n");
                htmlText.append("Amplitude: "+pg.getAmplitude()+"<br/>\n");
           
				html.setHtml(htmlText.toString());
                
                if (verbose) System.out.println("======= Input ===============\n"+htmlText.toString());
				htmlVariable.getTypes().add(access.getType(TypesPackage.Literals.HTML_TYPE));
				htmlVariable.getInitialValues().put(access.getType(TypesPackage.Literals.HTML_TYPE), html);
				((CompositeType) pulseGenerator).getVariables().add(htmlVariable);
			}
		}
	}

	/**
	 * @param component
	 * @return the NeuroML cell corresponding to a given LEMS component
	 */
	private Standalone getNeuroMLIonChannel(Component component)
	{
		String lemsId = component.getID();
		for(IonChannel c : neuroMLDocument.getIonChannel())
		{
			if(c.getId().equals(lemsId))
			{
				return c;
			}
		}
		for(IonChannelHH c : neuroMLDocument.getIonChannelHH())
		{
			if(c.getId().equals(lemsId))
			{
				return c;
			}
		}
		// for(IonChannelKS c : neuroMLDocument.getIonChannelKS())
		// {
		// if(c.getId().equals(lemsId))
		// {
		// return c;
		// }
		// }
		return null;
	}

	private void addExpresionNodes(CompositeType ionChannel) throws NeuroMLException, LEMSException, GeppettoVisitingException, ModelInterpreterException
	{
		// Get lems component and convert to neuroml
		Component component = ((Component) ionChannel.getDomainModel().getDomainModel());
		Standalone neuromlIonChannel = getNeuroMLIonChannel(component);

		// Create channel info extractor from export library
		if(neuromlIonChannel != null)
		{
			ChannelInfoExtractor channelInfoExtractor = new ChannelInfoExtractor((IonChannel) neuromlIonChannel);
			InfoNode gatesNode = channelInfoExtractor.getGates();
			for(Map.Entry<String, Object> entry : gatesNode.getProperties().entrySet())
			{
				String id = entry.getKey().substring(entry.getKey().lastIndexOf(" ") + 1);
				for(Variable gateVariable : ionChannel.getVariables())
				{
					if(gateVariable.getId().equals(id))
					{
						InfoNode gateNode = (InfoNode) entry.getValue();
						for(Map.Entry<String, Object> gateProperties : gateNode.getProperties().entrySet())
						{
							if(gateProperties.getValue() instanceof ExpressionNode)
							{
								// Match property id in export lib with neuroml id
								ResourcesSummary gatePropertyResources = ResourcesSummary.getValueByValue(gateProperties.getKey());
								if(gatePropertyResources != null)
								{
									CompositeType gateType = (CompositeType) gateVariable.getAnonymousTypes().get(0);
									for(Variable rateVariable : gateType.getVariables())
									{
										if(rateVariable.getId().equals(gatePropertyResources.getNeuromlId()))
										{
											CompositeType rateType = (CompositeType) rateVariable.getAnonymousTypes().get(0);
											// Create expression node
											Variable variable = getExpressionVariable(gateProperties.getKey(), (ExpressionNode) gateProperties.getValue());
											rateType.getVariables().add(variable);

											if(!((ExpressionNode) gateProperties.getValue()).getExpression().startsWith("org.neuroml.export"))
											{
												List<Variable> variables = this.plottableVariables.get(ionChannel.getName());
												if(variables == null) variables = new ArrayList<Variable>();
												variables.add(variable);
												this.plottableVariables.put(ionChannel.getName(), variables);
											}
										}
									}

								}
								else
								{
									throw new ModelInterpreterException("No node matches summary gate rate");
								}
							}
						}
					}
				}
			}
		}
	}

	private Variable getExpressionVariable(String expressionNodeId, ExpressionNode expressionNode) throws GeppettoVisitingException
	{

		Argument argument = valuesFactory.createArgument();
		argument.setArgument("v");

		Expression expression = valuesFactory.createExpression();
		expression.setExpression(expressionNode.getExpression());

		Function function = valuesFactory.createFunction();
		function.setExpression(expression);
		function.getArguments().add(argument);
		PlotMetadataNode plotMetadataNode = expressionNode.getPlotMetadataNode();
		if(plotMetadataNode != null)
		{
			FunctionPlot functionPlot = valuesFactory.createFunctionPlot();
			functionPlot.setTitle(plotMetadataNode.getPlotTitle());
			functionPlot.setXAxisLabel(plotMetadataNode.getXAxisLabel());
			functionPlot.setYAxisLabel(plotMetadataNode.getYAxisLabel());
			functionPlot.setInitialValue(plotMetadataNode.getInitialValue());
			functionPlot.setFinalValue(plotMetadataNode.getFinalValue());
			functionPlot.setStepValue(plotMetadataNode.getStepValue());
			function.setFunctionPlot(functionPlot);
		}

		Dynamics dynamics = valuesFactory.createDynamics();
		dynamics.setDynamics(function);

		Variable variable = variablesFactory.createVariable();
		variable.setId(ModelInterpreterUtils.parseId(expressionNodeId));
		variable.setName(expressionNodeId);
		variable.getInitialValues().put(access.getType(TypesPackage.Literals.DYNAMICS_TYPE), dynamics);
		variable.getTypes().add(access.getType(TypesPackage.Literals.DYNAMICS_TYPE));

		return variable;
	}

}