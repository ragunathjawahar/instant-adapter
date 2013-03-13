/*
 * Copyright © 2013 Mobs and Geeks
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobsandgeeks.adapters;

import android.content.Context;
import android.text.Html;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Class that constructs a custom {@link Adapter} by mapping <b>Instant*</b> annotated
 * methods from you model to {@link Views} in your layout. Methods can be annotated using the
 * {@link InstantText} or {@link InstantImage} annotation.
 *
 * @author Ragunath Jawahar <rj@mobsandgeeks.com>
 * @since 0.5
 * @version 0.5
 *
 * @param <T> The model you want to back using the {@link InstantAdapter}.
 */
public class InstantAdapter<T> extends ArrayAdapter<T> {

    // Debug
    static final String LOG_TAG = InstantAdapter.class.getSimpleName();
    static final boolean DEBUG = true;
    static void ifd(final String message) { if(DEBUG) Log.d(LOG_TAG, message); };

    // Constants
    private static final String EMPTY_STRING = "";

    // Attributes
    private Context mContext;
    private int mLayoutResourceId;
    private LayoutInflater mLayoutInflater;
    private Class<?> mDataType;
    private Set<Integer> mAnnotatedViewIds;
    private SparseArray<Evaluator<T>> mEvaluators;

    // Caches
    private SparseArray<Meta> mViewIdsAndMetaCache;
    private SparseArray<SimpleDateFormat> mDateFormatCache;
    private SparseArray<String> mFormatStringCache;

    /**
     * Interface that provides a mechanism to customize Views. You can setup one {@link Evaluator}
     * per {@link View}.
     *
     * @param <T> The model class you will be evaluating for customizing a {@link View}.
     */
    public interface Evaluator<T> {

        /**
         * Allows you to make changes to an associated View by supplying the adapter, the View's
         * parent, an instance of the data associated with the position and the position itself.
         *
         * @param adapter The {@link InstantAdapter} to which this {@link Evaluator} is assigned.
         * @param parent Parent for the View that is being evaluated. Usually the custom layout
         *          instance for individual views and a {@link ListView} or a {@link GridView}
         *          for the layout.
         * @param view The {@link View} that is being evaluated.
         * @param instance The instance that is associated with the current position.
         * @param position Position of the item within the adapter's data set.
         */
        void evaluate(InstantAdapter<T> adapter, View parent, View view, T instance, int position);
    }

    /**
     * Constructs a new {@link InstantAdapter} for your model.
     *
     * @param context The {@link Context} to use.
     * @param layoutResourceId The resource id of your XML layout.
     * @param dataType The data type backed by your adapter.
     * @param list {@link List} to be backed by your {@link InstantAdapter}.
     *
     * @throws IllegalArgumentException If {@code context} is null or {@code layoutResourceId} is
     *          invalid or {@code type} is {@code null}.
     */
    public InstantAdapter(final Context context, final int layoutResourceId,
            final Class<?> dataType, final List<T> list) {
        super(context, layoutResourceId, list);
        if (context == null) {
            throw new IllegalArgumentException("'context' cannot be null.");
        } else if (layoutResourceId == View.NO_ID || layoutResourceId == 0) {
            throw new IllegalArgumentException("Invalid 'layoutResourceId', please check again.");
        } else if (dataType == null) {
            throw new IllegalArgumentException("'dataType' cannot be null.");
        }

        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(context);
        mDataType = dataType;
        mEvaluators = new SparseArray<Evaluator<T>>();
        mAnnotatedViewIds = new HashSet<Integer>();
        mViewIdsAndMetaCache = new SparseArray<Meta>();
        mDateFormatCache = new SparseArray<SimpleDateFormat>();
        mFormatStringCache = new SparseArray<String>();

        // Setup
        findAnnotatedMethods();
    }

    /**
     * Returns a {@link View} that has been inflated from the {@code layoutResourceId} argument in
     * the constructor. This method handles recycling as well. Subclasses are recommended to chain
     * upwards by calling {@code super.getView(position, convertView, parent)} and abstain from
     * recycling views unless and until you know what you are doing ;)
     * <p>
     * You are free to use the {@code View.setTag()} and {@code View.getTag()} methods in your
     * subclasses.
     * </p>
     */
    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        View view = convertView;
        SparseArray<Holder> holders = null;
        T instance = getItem(position);

        if (view == null) {
            view = mLayoutInflater.inflate(mLayoutResourceId, null);
            holders = new SparseArray<Holder>();

            int size = mViewIdsAndMetaCache.size();
            for (int i = 0; i < size; i++) {
                int viewId = mViewIdsAndMetaCache.keyAt(i);
                Meta meta = mViewIdsAndMetaCache.get(viewId);
                View viewFromLayout = view.findViewById(viewId);
                if (viewFromLayout == null) {
                    String message = String.format("Cannot find View, check the 'viewId' " +
                            "attribute  on method %s.%s()",
                                mDataType.getSimpleName(), meta.method.getName());
                    throw new IllegalStateException(message);
                }
                holders.append(viewId, new Holder(viewFromLayout, meta));
                mAnnotatedViewIds.add(viewId);
            }
            view.setTag(mLayoutResourceId, holders);
        } else {
            holders = (SparseArray<Holder>) view.getTag(mLayoutResourceId);
        }

        updateAnnotatedViews(holders, view, instance, position);
        executeEvaluators(holders, parent, view, instance, position);

        return view;
    }

    /**
     * Sets an {@link Evaluator} for a given View id.
     *
     * @param viewId Designated View id.
     * @param evaluator An Evaluator for the corresponding View.
     *
     * @throws IllegalArgumentException If evaluator is {@code null}.
     */
    public void setEvaluator(final int viewId, final Evaluator<T> evaluator) {
        if (evaluator == null) {
            throw new IllegalArgumentException("'evaluator' cannot be null.");
        }
        mEvaluators.put(viewId, evaluator);
    }

    /**
     * Gets the {@link Evaluator} associated with the View id.
     *
     * @param viewId The {@link View} id whose {@link Evaluator} we are looking for.
     *
     * @return The Evaluator or {@code null} if one does not exist.
     */
    public Evaluator<T> getEvaluator(final int viewId) {
        return mEvaluators.get(viewId);
    }

    /**
     * Gets all Evaluators associated with this {@link InstantAdapter}.
     *
     * @return A {@link SparseArray} containing the all Evaluators.
     */
    public SparseArray<Evaluator<T>> getEvaluators() {
        return mEvaluators;
    }

    /**
     * Remove an {@link Evaluator} associated with the given view id.
     *
     * @param viewId The View id whose Evaluator has to be removed.
     */
    public void removeEvaluator(final int viewId) {
        mEvaluators.remove(viewId);
    }

    /**
     * Removes all Evaluators that are associated with this {@link InstantAdapter}.
     */
    public void removeAllEvaluators() {
        int size = mEvaluators.size();
        for (int i = 0; i < size; i++) {
            int key = mEvaluators.keyAt(i);
            mEvaluators.remove(key);
        }
    }

    /**
     * You should have used this a zillion times if you were doing it right. In case you
     * didn't, check this 2009 Google IO video - http://www.youtube.com/watch?v=N6YdwzAvwOA
     */
    private static class Holder {
        View view;
        Meta meta;

        Holder(final View view, final Meta meta) {
            this.view = view;
            this.meta = meta;
        }
    }

    /**
     * Class holds reference to a View's annotation and it's annotated method.
     */
    private static class Meta {
        Annotation annotation;
        Method method;

        Meta(final Annotation annotation, final Method method) {
            this.annotation = annotation;
            this.method = method;
        }
    }

    private void findAnnotatedMethods() {
        Method[] annotatedMethods = mDataType.getDeclaredMethods();
        for (Method method : annotatedMethods) {
            Annotation[] annotations = method.getAnnotations();
            for (Annotation annotation : annotations) {
                if (isInstantAnnotation(annotation)) {
                    // Assertions
                    assertMethodIsPublic(method);
                    assertNoParamsOrSingleContextParam(method);
                    assertNonVoidReturnType(method);

                    // TODO Check if view type is compatible with annotation
                    Meta meta = new Meta(annotation, method);
                    if (annotation instanceof InstantText) {
                        mViewIdsAndMetaCache.append(((InstantText) annotation).viewId(), meta);
                    }
                }
            }
        }
    }

    private boolean isInstantAnnotation(final Annotation annotation) {
        return annotation.annotationType().equals(InstantText.class);
    }

    private void assertMethodIsPublic(final Method method) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalStateException(String.format("%s.%s() should be public",
                            mDataType.getSimpleName(), method.getName()));
        }
    }

    private void assertNoParamsOrSingleContextParam(final Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        final int nParameters = parameters.length;
        if (nParameters > 0) {
            String errorMessage = String.format("%s.%s() can have a single Context " +
                    "parameter or should have no parameters.",
                        mDataType.getSimpleName(), method.getName());
            if (parameters.length == 1) {
                Class<?> parameterType = parameters[0];
                if (!parameterType.isAssignableFrom(Context.class)) {
                    throw new IllegalStateException(errorMessage);
                }
            } else if (parameters.length > 1) {
                throw new IllegalStateException(errorMessage);
            }
        }
    }

    private void assertNonVoidReturnType(final Method method) {
        if (method.getReturnType().equals(Void.TYPE)) {
            throw new UnsupportedOperationException(
                    String.format("Methods with void return types cannot be annotated, " +
                            "check %s.%s()", mDataType.getSimpleName(), method.getName()));
        }
    }

    private void updateAnnotatedViews(final SparseArray<Holder> holders, final View parent,
            final T instance, final int position) {
        int nHolders = holders.size();
        for (int i = 0; i < nHolders; i++) {
            Holder holder = holders.valueAt(i);
            Meta meta = holder.meta;
            if (meta == null) continue; // Evaluator-only views will have a null meta

            Object returnValue = invokeReflectedMethod(meta.method, instance);

            // Update view from data
            Class<? extends View> viewType = holder.view.getClass();
            if (viewType.isAssignableFrom(TextView.class)) {
                updateTextView(holder, returnValue);
            }

            // Evaluators for child views
            Evaluator<T> evaluator = mEvaluators.get(holder.view.getId());
            if (evaluator != null) {
                evaluator.evaluate(this, parent, holder.view, instance, position);
            }
        }
    }

    private Object invokeReflectedMethod(final Method method, final T instance) {
        Object returnValue = null;

        try {
            Class<?>[] parameterTypes = method.getParameterTypes();
            int nParameters = parameterTypes.length;
            if (nParameters == 0) {
                returnValue = method.invoke(instance);
            } else if (nParameters == 1) {
                returnValue = method.invoke(instance, mContext);
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return returnValue;
    }

    private void updateTextView(final Holder holder, final Object returnValue) {
        InstantText instantText = (InstantText) holder.meta.annotation;
        TextView textView = (TextView) holder.view;
        int viewId = textView.getId();

        String text = null;
        if (returnValue != null) {
            text = applyDatePattern(viewId, instantText, returnValue);
            text = applyFormatString(viewId, instantText, text, returnValue);
            if (text == null) {
                text = returnValue.toString();
            }
        }

        textView.setText(instantText.html() ? Html.fromHtml(text) : text);
    }

    private String applyDatePattern(final int viewId, final InstantText instantText,
            final Object returnValue) {
        int index = mDateFormatCache.indexOfKey(viewId);
        SimpleDateFormat simpleDateFormat = null;
        String text = null;

        if (index > -1) {
            simpleDateFormat = mDateFormatCache.get(viewId);
        } else {
            int datePatternRes = instantText.datePatternResId();
            String datePattern = datePatternRes != 0 ?
                    mContext.getString(datePatternRes) : instantText.datePattern();

            if (datePattern != null && !EMPTY_STRING.equals(datePattern)) {
                simpleDateFormat = new SimpleDateFormat(datePattern, Locale.getDefault());
                mDateFormatCache.put(viewId, simpleDateFormat);
            }
        }

        if (simpleDateFormat != null) {
            text = simpleDateFormat.format(returnValue);
        }

        return text;
    }

    private String applyFormatString(final int viewId, final InstantText instantText,
            final String dateFormattedString, final Object returnValue) {
        int index = mFormatStringCache.indexOfKey(viewId);
        String formatString = null;
        String formatted = dateFormattedString;

        if (index > -1) {
            formatString = mFormatStringCache.get(viewId);
        } else {
            int formatStringRes = instantText.formatStringResId();
            formatString = formatStringRes != 0 ?
                    mContext.getString(formatStringRes) : instantText.formatString();
            mFormatStringCache.put(viewId, formatString);
        }

        if (formatString != null && !EMPTY_STRING.equals(formatString)) {
            formatted = String.format(formatString, dateFormattedString != null ?
                    dateFormattedString : returnValue);
        }

        return formatted;
    }

    private void executeEvaluators(final SparseArray<Holder> holders,
            final View parent, final View view, final T instance, final int position) {
        int nEvaluators = mEvaluators.size();
        for (int i = 0; i < nEvaluators; i++) {
            int viewId = mEvaluators.keyAt(i);
            Evaluator<T> evaluator = mEvaluators.get(viewId);

            if (evaluator == null) continue;

            if (viewId == mLayoutResourceId) {
                evaluator.evaluate(this, parent, view, instance, position);
            } else {
                Holder holder = holders.get(viewId);
                View viewWithId = null;
                if (holder != null) {
                    viewWithId = holder.view;
                } else {
                    viewWithId = view.findViewById(viewId);
                    holders.append(viewId, new Holder(viewWithId, null));
                }
                evaluator.evaluate(this, view, viewWithId, instance, position);
            }
        }
    }

}
