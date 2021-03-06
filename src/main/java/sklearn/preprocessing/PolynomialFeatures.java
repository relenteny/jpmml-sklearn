/*
 * Copyright (c) 2017 Villu Ruusmann
 *
 * This file is part of JPMML-SkLearn
 *
 * JPMML-SkLearn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-SkLearn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-SkLearn.  If not, see <http://www.gnu.org/licenses/>.
 */
package sklearn.preprocessing;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.TypeDefinitionField;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.PowerFeature;
import org.jpmml.converter.ValueUtil;
import org.jpmml.sklearn.ClassDictUtil;
import org.jpmml.sklearn.SkLearnEncoder;
import sklearn.Transformer;

public class PolynomialFeatures extends Transformer {

	public PolynomialFeatures(String module, String name){
		super(module, name);
	}

	@Override
	public List<Feature> encodeFeatures(List<String> ids, List<Feature> features, final SkLearnEncoder encoder){
		int numberOfInputFeatures = getNumberOfInputFeatures();
		int numberOfOutputFeatures = getNumberOfOutputFeatures();

		ClassDictUtil.checkSize(numberOfInputFeatures, ids, features);

		final
		int degree = getDegree();

		boolean includeBias = getIncludeBias();
		boolean interactionOnly = getInteractionOnly();

		List<int[]> powers = new ArrayList<>();

		for(int i = (includeBias ? 0 : 1); i <= degree; i++){
			List<int[]> degreePowers;

			if(interactionOnly){
				degreePowers = combinations(numberOfInputFeatures, i);
			} else

			{
				degreePowers = combinations_with_replacement(numberOfInputFeatures, i);
			}

			powers.addAll(degreePowers);
		}

		ClassDictUtil.checkSize(numberOfOutputFeatures, powers);

		FieldName unitName = FieldName.create("constant(1.0d)");

		DerivedField unitField = encoder.getDerivedField(unitName);
		if(unitField == null){
			unitField = encoder.createDerivedField(unitName, PMMLUtil.createConstant(1d));
		}

		Function<Feature, Feature[]> function = new Function<Feature, Feature[]>(){

			@Override
			public Feature[] apply(Feature feature){
				Feature[] features = new Feature[degree];

				features[0] = feature;

				TypeDefinitionField field = encoder.getField(feature.getName());

				for(int i = 2; i <= degree; i++){
					features[i - 1] = new PowerFeature(encoder, field, i);
				}

				return features;
			}
		};

		List<Feature[]> transformedFeatures = new ArrayList<>(Lists.transform(features, function));

		List<Feature> result = new ArrayList<>();

		for(int[] power : powers){
			StringBuilder sb = new StringBuilder();

			String sep = "";

			List<String> powerIds = new ArrayList<>();
			List<Feature> powerFeatures = new ArrayList<>();

			for(int i = 0; i < power.length; i++){
				String id = ids.get(i);
				Feature feature = features.get(i);

				if(power[i] >= 1){
					sb.append(sep);

					sep = ":";

					sb.append(id).append(power[i] > 1 ? ("^" + power[i]) : "");

					powerIds.add(id);
					powerFeatures.add(transformedFeatures.get(i)[power[i] - 1]);
				}
			}

			if(powerFeatures.size() == 0){
				ids.add(unitName.getValue());
				result.add(new ContinuousFeature(encoder, unitField));
			} else

			if(powerFeatures.size() == 1){
				ids.add(Iterables.getOnlyElement(powerIds));
				result.add(Iterables.getOnlyElement(powerFeatures));
			} else

			{
				String id = sb.toString();

				ids.add(id);
				result.add(new InteractionFeature(encoder, FieldName.create(id), DataType.DOUBLE, powerFeatures));
			}
		}

		ids.subList(0, features.size()).clear();

		return result;
	}

	public int getDegree(){
		return ValueUtil.asInt((Number)get("degree"));
	}

	public boolean getIncludeBias(){
		return (Boolean)get("include_bias");
	}

	public boolean getInteractionOnly(){
		return (Boolean)get("interaction_only");
	}

	public int getNumberOfInputFeatures(){
		return ValueUtil.asInt((Number)get("n_input_features_"));
	}

	public int getNumberOfOutputFeatures(){
		return ValueUtil.asInt((Number)get("n_output_features_"));
	}

	/**
	 * @see https://docs.python.org/2/library/itertools.html#itertools.combinations
	 */
	static
	private List<int[]> combinations(int n, int r){
		List<int[]> result = new ArrayList<>();

		int[] indices = new int[r];

		for(int i = 0; i < r; i++){
			indices[i] = i;
		}

		result.add(power(n, indices));

		while(true){
			int i = (r - 1);

			for(; i > -1; i--){

				if(indices[i] != (i + n - r)){
					break;
				}
			}

			if(i < 0){
				break;
			}

			indices[i] += 1;

			for(int j = (i + 1); j < r; j++){
				indices[j] = (indices[j - 1] + 1);
			}

			result.add(power(n, indices));
		}

		return result;
	}

	/**
	 * @see https://docs.python.org/2/library/itertools.html#itertools.combinations_with_replacement
	 */
	static
	private List<int[]> combinations_with_replacement(int n, int r){
		List<int[]> result = new ArrayList<>();

		int[] indices = new int[r];

		result.add(power(n, indices));

		while(true){
			int i = (r - 1);

			for(; i > -1; i--){

				if(indices[i] != (n - 1)){
					break;
				}
			}

			if(i < 0){
				break;
			}

			int value = (indices[i] + 1);

			for(int j = i; j < r; j++){
				indices[j] = value;
			}

			result.add(power(n, indices));
		}

		return result;
	}

	static
	private int[] power(int n, int[] indices){
		int[] result = new int[n];

		for(int index : indices){
			result[index] += 1;
		}

		return result;
	}
}