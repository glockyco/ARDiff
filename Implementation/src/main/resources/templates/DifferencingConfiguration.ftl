<#-- @ftlvariable name="parameters" type="equiv.checking.DifferencingParameters" -->

target = ${parameters.targetNamespace}.${parameters.targetClassName}
# @TODO: Generate correct number of symbolic parameters.
symbolic.method = ${parameters.targetNamespace}.${parameters.targetClassName}.run(${parameters.symbolicParameters})
# @TODO: Add missing classpath directories / JARs.
classpath=target/classes
# @TODO: Read symbolic execution settings parameters.
symbolic.min_int=-100
symbolic.max_int=100
symbolic.min_long=-100
symbolic.max_long=100
symbolic.min_double=-100.0
symbolic.max_double=100.0
symbolic.debug = false
symbolic.optimizechoices = false
symbolic.lazy=on
symbolic.arrays=true
symbolic.strings = true
symbolic.dp=coral
symbolic.string_dp_timeout_ms=300000
search.depth_limit=5
listener = gov.nasa.jpf.symbc.SymbolicListener
search.multiple_errors=true
search.class = .search.CustomSearch
