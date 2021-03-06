/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.JavaClassSupers;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class JavaClassSupersImpl extends JavaClassSupers {

  @Nullable
  public PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass,
                                                 @NotNull PsiClass derivedClass,
                                                 @NotNull GlobalSearchScope scope,
                                                 @NotNull PsiSubstitutor derivedSubstitutor) {
    if (InheritanceImplUtil.hasObjectQualifiedName(superClass)) return PsiSubstitutor.EMPTY;

    return derivedClass instanceof PsiTypeParameter
           ? processTypeParameter((PsiTypeParameter)derivedClass, scope, superClass, ContainerUtil.<PsiTypeParameter>newTroveSet(), derivedSubstitutor)
           : getSuperSubstitutorWithCaching(superClass, derivedClass, scope, derivedSubstitutor);
  }

  @Nullable
  private static PsiSubstitutor getSuperSubstitutorWithCaching(@NotNull PsiClass superClass,
                                                               @NotNull PsiClass derivedClass,
                                                               @NotNull GlobalSearchScope resolveScope,
                                                               @NotNull PsiSubstitutor derivedSubstitutor) {
    PsiSubstitutor substitutor = ScopedClassHierarchy.getSuperClassSubstitutor(derivedClass, resolveScope, superClass);
    if (substitutor == null) return null;
    if (PsiUtil.isRawSubstitutor(derivedClass, derivedSubstitutor)) return createRawSubstitutor(superClass);

    return composeSubstitutors(derivedSubstitutor, substitutor, superClass);
  }

  @NotNull
  static PsiSubstitutor createRawSubstitutor(@NotNull PsiClass superClass) {
    return JavaPsiFacade.getElementFactory(superClass.getProject()).createRawSubstitutor(superClass);
  }

  @NotNull
  private static PsiSubstitutor composeSubstitutors(PsiSubstitutor outer, PsiSubstitutor inner, PsiClass onClass) {
    PsiSubstitutor answer = PsiSubstitutor.EMPTY;
    Map<PsiTypeParameter, PsiType> outerMap = outer.getSubstitutionMap();
    Map<PsiTypeParameter, PsiType> innerMap = inner.getSubstitutionMap();
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(onClass)) {
      if (outerMap.containsKey(parameter) || innerMap.containsKey(parameter)) {
        answer = answer.put(parameter, outer.substitute(inner.substitute(parameter)));
      }
    }
    return answer;
  }

  /**
   * Some type parameters (e.g. {@link com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable} change their supers at will,
   * so caching the hierarchy is impossible. 
   */
  @Nullable
  private static PsiSubstitutor processTypeParameter(PsiTypeParameter parameter,
                                                     GlobalSearchScope scope,
                                                     PsiClass superClass,
                                                     Set<PsiTypeParameter> visited, 
                                                     PsiSubstitutor derivedSubstitutor) {
    if (parameter.getManager().areElementsEquivalent(parameter, superClass)) return PsiSubstitutor.EMPTY;
    if (!visited.add(parameter)) return null;

    for (PsiClassType type : parameter.getExtendsListTypes()) {
      PsiClassType.ClassResolveResult result = type.resolveGenerics();
      PsiClass psiClass = result.getElement();
      if (psiClass == null) continue;

      PsiSubstitutor answer;
      if (psiClass instanceof PsiTypeParameter) {
        answer = processTypeParameter((PsiTypeParameter)psiClass, scope, superClass, visited, derivedSubstitutor);
        if (answer != null) {
          return answer;
        }
      }
      else {
        answer = getSuperSubstitutorWithCaching(superClass, psiClass, scope, result.getSubstitutor());
        if (answer != null) {
          return composeSubstitutors(derivedSubstitutor, answer, superClass);
        }
      }
    }

    return null;
  }

}
